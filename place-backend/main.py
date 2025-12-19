from fastapi import FastAPI, HTTPException, Depends, status
from fastapi_cache import FastAPICache
from fastapi_cache.decorator import cache
from fastapi_cache.backends.inmemory import InMemoryBackend 
from pydantic import BaseModel
from typing import List, Optional
from datetime import datetime, timedelta
from places_data import places_db
import hashlib
import smtplib
import sqlite3
import os
import aiosqlite
from fastapi.security import OAuth2PasswordBearer
from fastapi import Form
from contextlib import contextmanager
from fastapi import UploadFile, File
from fastapi.staticfiles import StaticFiles
from email.mime.text import MIMEText

app = FastAPI()

app.mount("/avatars", StaticFiles(directory="avatars"), name="avatars")

AVATARS_DIR = "avatars"
os.makedirs(AVATARS_DIR, exist_ok=True)
DATABASE_URL = "users.db"

reset_codes = {}
SMTP_SERVER = "smtp.gmail.com"
SMTP_PORT = 587 
SMTP_USER = "guideforuapp@gmail.com"
SMTP_PASSWORD = "bwyf wukr zipk yplp"
USE_TLS = True
CODE_EXPIRE_MINUTES = 10

class User(BaseModel):
    id: Optional[int] = None
    name: str
    email: str
    avatar_uri: Optional[str] = None

class UserCreate(User):
    password: str

class UserInDB(User):
    password_hash: str

class UserUpdate(BaseModel):
    name: Optional[str] = None
    avatar_uri: Optional[str] = None

class Comment(BaseModel):
    user_id: int
    comment: str
    landmark_id: int
    date: str

class Review(BaseModel):
    landmark_id: int
    comment: str
    date: str

class Favorite(BaseModel):
    user_id: int
    landmark_id: int
    
async def init_db():
    async with aiosqlite.connect(DATABASE_URL) as db:
        await db.execute('''
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                email TEXT UNIQUE,
                password_hash TEXT NOT NULL,
                avatar_uri TEXT DEFAULT '@drawable/avatar2'
            )
        ''')
        await db.execute('''
            CREATE TABLE IF NOT EXISTS comments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                comment TEXT NOT NULL,
                landmark_id INTEGER NOT NULL,
                date TEXT NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            )
        ''')
        await db.execute('''
            CREATE TABLE IF NOT EXISTS favorites (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                landmark_id INTEGER NOT NULL,
                UNIQUE(user_id, landmark_id),
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            )
        ''')
        await db.commit()

@app.on_event("startup")
async def startup():
    await init_db()
    FastAPICache.init(InMemoryBackend())

async def get_db():
    async with aiosqlite.connect(DATABASE_URL) as db:
        db.row_factory = aiosqlite.Row
        yield db

@app.get("/")
async def root():
    return {
        "message": "Welcome to Place API", 
        "endpoints": {
            "register": "/users/register (POST)",
            "login": "/users/login (POST)",
            "user_profile": "/users/{email} (GET)"
        }
    }

@app.post("/favorites")
async def add_favorite(favorite: Favorite, db: aiosqlite.Connection = Depends(get_db)):
    try:
        await db.execute(
            "INSERT INTO favorites (user_id, landmark_id) VALUES (?, ?)",
            (favorite.user_id, favorite.landmark_id)
        )
        await db.commit()
        return {"message": "Favorite added successfully"}
    except aiosqlite.IntegrityError:
        raise HTTPException(status_code=400, detail="Favorite already exists")

@app.delete("/favorites")
async def remove_favorite(
    user_id: int,
    landmark_id: int,
    db: aiosqlite.Connection = Depends(get_db)
):
    async with db.execute(
        "DELETE FROM favorites WHERE user_id = ? AND landmark_id = ?",
        (user_id, landmark_id)
    ) as cursor:
        if cursor.rowcount == 0:
            raise HTTPException(status_code=404, detail="Favorite not found")
    
    await db.commit()
    return {"message": "Favorite removed successfully"}

@app.get("/favorites/{user_id}")
async def get_favorites(user_id: int, db: aiosqlite.Connection = Depends(get_db)):
    favorites = []
    async with db.execute(
        "SELECT landmark_id FROM favorites WHERE user_id = ?",
        (user_id,)
    ) as cursor:
        async for row in cursor:
            favorites.append(row["landmark_id"])
    
    return {"favorites": favorites}

@app.post("/users/register", status_code=status.HTTP_201_CREATED)
async def register_user(user: UserCreate, db: aiosqlite.Connection = Depends(get_db)):
    async with db.execute("SELECT email FROM users WHERE email = ?", (user.email,)) as cursor:
        if await cursor.fetchone():
            raise HTTPException(status_code=400, detail="Email already registered")
    
    password_hash = hash_password(user.password)
    
    cursor = await db.execute(
        "INSERT INTO users (name, email, password_hash, avatar_uri) VALUES (?, ?, ?, ?)",
        (user.name, user.email, password_hash, user.avatar_uri)
    )
    await db.commit()
    
    return {"message": "User created successfully"}

@app.post("/users/login")
async def login_user(
    email: str = Form(...),
    password: str = Form(...),
    db: aiosqlite.Connection = Depends(get_db)
):
    password_hash = hash_password(password)
    
    async with db.execute(
        "SELECT id, name, email, avatar_uri FROM users WHERE email = ? AND password_hash = ?",
        (email, password_hash)
    ) as cursor:
        user = await cursor.fetchone()
    
    if not user:
        raise HTTPException(status_code=401, detail="Invalid credentials")
    
    return {
        "id": user["id"],
        "name": user["name"],
        "email": user["email"],
        "avatar_uri": user["avatar_uri"]
    }

@app.post("/users/{user_id}/avatar")
async def upload_avatar(
    user_id: int,
    file: UploadFile = File(...),
    db: aiosqlite.Connection = Depends(get_db)
):
    async with db.execute("SELECT email FROM users WHERE id = ?", (user_id,)) as cursor:
        user = await cursor.fetchone()
        if not user:
            raise HTTPException(status_code=404, detail="User not found")
    
    file_ext = os.path.splitext(file.filename)[1]
    filename = f"{user['email']}{file_ext}"
    file_path = os.path.join(AVATARS_DIR, filename)
    
    with open(file_path, "wb") as f:
        f.write(await file.read())
    
    avatar_uri = f"/avatars/{filename}"
    await db.execute(
        "UPDATE users SET avatar_uri = ? WHERE id = ?",
        (avatar_uri, user_id)
    )
    await db.commit()
    
    return {"avatar_uri": avatar_uri}

@app.get("/users/{email}")
async def get_user(email: str, db: aiosqlite.Connection = Depends(get_db)):
    async with db.execute(
        "SELECT id, name, email, COALESCE(avatar_uri, '@drawable/avatar2') as avatar_uri FROM users WHERE email = ?",
        (email,)
    ) as cursor:
        user = await cursor.fetchone()
    
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    
    return {
        "id": user["id"],
        "name": user["name"],
        "email": user["email"],
        "avatar_uri": user["avatar_uri"]
    }

@app.put("/users/{user_id}")
async def update_user(
    user_id: int,
    update_data: UserUpdate,
    db: aiosqlite.Connection = Depends(get_db)
):
    updates = {}
    if update_data.name is not None:
        updates["name"] = update_data.name
    if update_data.avatar_uri is not None:
        updates["avatar_uri"] = update_data.avatar_uri
    
    if not updates:
        raise HTTPException(status_code=400, detail="No fields to update")
    
    set_clause = ", ".join(f"{k} = ?" for k in updates.keys())
    values = list(updates.values())
    values.append(user_id)
    
    async with db.execute(
        f"UPDATE users SET {set_clause} WHERE id = ?",
        values
    ) as cursor:
        if cursor.rowcount == 0:
            raise HTTPException(status_code=404, detail="User not found")
    
    await db.commit()
    return {"message": "User updated successfully"}

@app.put("/users/{user_id}/password")
async def update_password(
    user_id: int,
    old_password: str = Form(...),
    new_password: str  = Form(...),
    db: aiosqlite.Connection = Depends(get_db)
):
    old_password_hash = hash_password(old_password)
    
    async with db.execute(
        "SELECT id FROM users WHERE id = ? AND password_hash = ?",
        (user_id, old_password_hash)
    ) as cursor:
        if not await cursor.fetchone():
            raise HTTPException(status_code=401, detail="Invalid old password")
    
    new_password_hash = hash_password(new_password)
    await db.execute(
        "UPDATE users SET password_hash = ? WHERE id = ?",
        (new_password_hash, user_id)
    )
    await db.commit()
    
    return {"message": "Password updated successfully"}

@app.post("/password-reset/initiate")
async def initiate_password_reset(
    email: str = Form(...),
    db: aiosqlite.Connection = Depends(get_db)
):
    async with db.execute("SELECT id FROM users WHERE email = ?", (email,)) as cursor:
        user = await cursor.fetchone()
        if not user:
            raise HTTPException(status_code=404, detail="User not found")
    
    reset_code = generate_reset_code()
    expiration = datetime.now() + timedelta(minutes=CODE_EXPIRE_MINUTES)
    
    reset_codes[email] = {
        "code": reset_code,
        "expires": expiration,
        "user_id": user["id"]
    }
    
    try:
        send_success = send_reset_code(email, reset_code)
        if not send_success:
            raise HTTPException(
                status_code=500,
                detail="Failed to send reset code. Please try again later."
            )
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Email sending error: {str(e)}"
        )
    
    return {"message": "Reset code sent to your email"}

@app.post("/password-reset/verify")
async def verify_reset_code(
    email: str = Form(...),
    code: str = Form(...)
):
    if email not in reset_codes:
        raise HTTPException(status_code=404, detail="No reset request found for this email")
    
    reset_data = reset_codes[email]
    
    if datetime.now() > reset_data["expires"]:
        del reset_codes[email]
        raise HTTPException(status_code=400, detail="Reset code has expired")
    
    if code != reset_data["code"]:
        raise HTTPException(status_code=400, detail="Invalid reset code")
    
    return {"verified": True, "user_id": reset_data["user_id"]}

@app.post("/password-reset/complete")
async def complete_password_reset(
    user_id: int = Form(...),
    new_password: str = Form(...),
    db: aiosqlite.Connection = Depends(get_db)
):
    password_hash = hash_password(new_password)
    
    await db.execute(
        "UPDATE users SET password_hash = ? WHERE id = ?",
        (password_hash, user_id)
    )
    await db.commit()
    
    for email, data in list(reset_codes.items()):
        if data["user_id"] == user_id:
            del reset_codes[email]
            break
    
    return {"message": "Password has been reset successfully"}

def send_reset_code(email, code):
    msg_text = f"""
    Уважаемый пользователь!<br><br>

    Ваш код подтверждения для доступа к сервису: <strong>{code}</strong><br>

    Код действителен в течение 10 минут.
    Не передавайте его третьим лицам.<br>
    Если вы не запрашивали данный код, проигнорируйте это письмо.<br><br>

    С уважением,<br>
    Служба безопасности GuideForU
    """

    msg = MIMEText(msg_text, 'html', 'utf-8')
    msg['Subject'] = 'Код подтверждения для сброса пароля'
    msg['From'] = SMTP_USER
    msg['To'] = email
    
    try:
        with smtplib.SMTP(SMTP_SERVER, SMTP_PORT) as server:
            server.ehlo()
            server.starttls()
            server.ehlo()
            server.login(SMTP_USER, SMTP_PASSWORD)
            server.sendmail(SMTP_USER, [email], msg.as_string())
            return True
    except Exception as e:
        print(f"Ошибка отправки: {e}")
        return False
    
def generate_reset_code(length=6):
    import random
    return ''.join([str(random.randint(0, 9)) for _ in range(length)])

@app.delete("/users/{user_id}")
async def delete_user(
    user_id: int,
    db: aiosqlite.Connection = Depends(get_db)
):
    async with db.execute(
        "DELETE FROM users WHERE id = ?",
        (user_id,)
    ) as cursor:
        if cursor.rowcount == 0:
            raise HTTPException(status_code=404, detail="User not found")
    
    await db.commit()
    return {"message": "User deleted successfully"}

@app.post("/comments")
async def add_comment(
    comment: Comment,
    db: aiosqlite.Connection = Depends(get_db)
):
    current_date = datetime.now().strftime("%d.%m.%Y %H:%M")
    
    await db.execute(
        "INSERT INTO comments (user_id, comment, landmark_id, date) VALUES (?, ?, ?, ?)",
        (comment.user_id, comment.comment, comment.landmark_id, current_date)
    )
    await db.commit()
    return {"message": "Comment added successfully"}

@app.get("/comments/landmark/{landmark_id}")
async def get_comments_by_landmark(
    landmark_id: int,
    db: aiosqlite.Connection = Depends(get_db)
):
    comments = []
    
    async with db.execute(
        """SELECT c.user_id, c.comment, c.date, u.name, 
           COALESCE(u.avatar_uri, '@drawable/avatar2') as avatar_uri
           FROM comments c 
           JOIN users u ON c.user_id = u.id 
           WHERE c.landmark_id = ? 
           ORDER BY c.date DESC""",
        (landmark_id,)
    ) as cursor:
        async for row in cursor:
            comments.append({
                "user_id": row["user_id"],
                "comment": row["comment"],
                "date": row["date"],
                "user_name": row["name"],
                "avatar_uri": row["avatar_uri"] or "@drawable/avatar2"
            })
    
    return {"comments": comments}

@app.get("/comments/user/{user_id}")
async def get_user_reviews(
    user_id: int,
    db: aiosqlite.Connection = Depends(get_db)
):
    reviews = []
    
    async with db.execute(
        "SELECT landmark_id, comment, date FROM comments WHERE user_id = ? ORDER BY date DESC",
        (user_id,)
    ) as cursor:
        async for row in cursor:
            landmark_id = row[0]
            comment = row[1]
            date = row[2]
            
            landmark_name = "Unknown"
            for landmark in places_db.get("landmarks", []):
                if landmark["id"] == landmark_id:
                    landmark_name = landmark["name"]
                    break
                
            reviews.append({
                "landmark_id": landmark_id,
                "landmark_name": landmark_name,
                "comment": comment,
                "date": date
            })

    return {"reviews": reviews}

@app.delete("/comments")
async def delete_comment(
    user_id: int,
    landmark_id: int,
    comment: str,
    db: aiosqlite.Connection = Depends(get_db)
):
    async with db.execute(
        "DELETE FROM comments WHERE user_id = ? AND landmark_id = ? AND comment = ?",
        (user_id, landmark_id, comment)
    ) as cursor:
        if cursor.rowcount == 0:
            raise HTTPException(status_code=404, detail="Comment not found")
    
    await db.commit()
    return {"message": "Comment deleted successfully"}

@app.get("/landmarks")
@cache(expire=300)
def get_landmarks():
    return {
        "landmarks": places_db.get("landmarks", [])
    }

@app.get("/landmarks/{landmark_id}")
@cache(expire=300)
def get_landmark_by_id(landmark_id: int):
    for landmark in places_db.get("landmarks", []):
        if landmark["id"] == landmark_id:
            return landmark
    raise HTTPException(status_code=404, detail="Landmark not found")