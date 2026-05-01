import os
from flask import Flask, render_template, request, redirect, url_for, session
from flask_sqlalchemy import SQLAlchemy
from werkzeug.security import generate_password_hash, check_password_hash

# Initialize App
app = Flask(__name__)
app.config['SECRET_KEY'] = 'clarity_silver_key_777'

# Use an absolute path for the database to make sure Render finds it
basedir = os.path.abspath(os.path.dirname(__file__))
app.config['SQLALCHEMY_DATABASE_URI'] = 'sqlite:///' + os.path.join(basedir, 'clarity.db')
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False
app.config['UPLOAD_FOLDER'] = os.path.join(basedir, 'static/uploads')

db = SQLAlchemy(app)

# --- DATABASE MODELS ---
class User(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    username = db.Column(db.String(80), unique=True, nullable=False)
    password = db.Column(db.String(120), nullable=False)
    score = db.Column(db.Integer, default=0)

class Log(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey('user.id'))
    activity = db.Column(db.String(200))
    impact = db.Column(db.Integer)
    type = db.Column(db.String(50))

# --- DATABASE INITIALIZATION ---
# This "Force Create" ensures the tables exist before the first request
with app.app_context():
    db.create_all()
    if not os.path.exists(app.config['UPLOAD_FOLDER']):
        os.makedirs(app.config['UPLOAD_FOLDER'])

# --- ROUTES ---
@app.route('/')
def index():
    if 'user_id' not in session: return redirect(url_for('login'))
    user = User.query.get(session['user_id'])
    logs = Log.query.filter_by(user_id=user.id).order_by(Log.id.desc()).all()
    return render_template('index.html', user=user, logs=logs)

@app.route('/webhook', methods=['POST'])
def webhook():
    data = request.json
    app_name = data.get('app', 'Unknown App')
    user = User.query.first() 
    if not user: return "No user found. Please sign up first.", 200
    
    distractions = ['Instagram', 'TikTok', 'Snapchat', 'Facebook', 'Cooking Madness']
    impact = -1 if app_name in distractions else 1
    
    new_log = Log(user_id=user.id, activity=f"Opened {app_name}", impact=impact, type="Auto")
    user.score += impact
    db.session.add(new_log)
    db.session.commit()
    return "The fog returns. Choose Clarity." if impact < 0 else "Focus maintained."

@app.route('/signup', methods=['GET', 'POST'])
def signup():
    if request.method == 'POST':
        # Check if user already exists to prevent errors
        existing_user = User.query.filter_by(username=request.form['username']).first()
        if existing_user:
            return "Username already exists. Try another."
            
        pw = generate_password_hash(request.form['password'])
        new_user = User(username=request.form['username'], password=pw)
        db.session.add(new_user)
        db.session.commit()
        return redirect(url_for('login'))
    return render_template('signup.html')

@app.route('/login', methods=['GET', 'POST'])
def login():
    if request.method == 'POST':
        user = User.query.filter_by(username=request.form['username']).first()
        if user and check_password_hash(user.password, request.form['password']):
            session['user_id'] = user.id
            return redirect(url_for('index'))
    return render_template('login.html')

@app.route('/vault')
def vault():
    if 'user_id' not in session: return redirect(url_for('login'))
    return render_template('vault.html')

@app.route('/logout')
def logout():
    session.pop('user_id', None)
    return redirect(url_for('login'))

if __name__ == '__main__':
    port = int(os.environ.get("PORT", 10000))
    app.run(host='0.0.0.0', port=port)