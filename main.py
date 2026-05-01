import os
from flask import Flask, render_template, request, redirect, url_for, session
from flask_sqlalchemy import SQLAlchemy
from werkzeug.security import generate_password_hash, check_password_hash

app = Flask(__name__)
app.config['SECRET_KEY'] = 'clarity_silver_key_777'
basedir = os.path.abspath(os.path.dirname(__file__))
app.config['SQLALCHEMY_DATABASE_URI'] = 'sqlite:///' + os.path.join(basedir, 'clarity.db')
app.config['UPLOAD_FOLDER'] = 'static/uploads'

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
    type = db.Column(db.String(50)) # Manual or Auto

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
    # Automatically links to the first user created (You)
    user = User.query.first() 
    if not user: return "No User", 400
    
    distractions = ['Instagram', 'TikTok', 'Snapchat', 'Facebook', 'Cooking Madness']
    impact = -1 if app_name in distractions else 1
    
    new_log = Log(user_id=user.id, activity=f"Opened {app_name}", impact=impact, type="Auto")
    user.score += impact
    db.session.add(new_log)
    db.session.commit()
    
    # This message is what MacroDroid will show/speak
    return "The fog returns. Choose Clarity." if impact < 0 else "Focus maintained. Ascent continues."

@app.route('/vault')
def vault():
    if 'user_id' not in session: return redirect(url_for('login'))
    return render_template('vault.html')

@app.route('/login', methods=['GET', 'POST'])
def login():
    if request.method == 'POST':
        user = User.query.filter_by(username=request.form['username']).first()
        if user and check_password_hash(user.password, request.form['password']):
            session['user_id'] = user.id
            return redirect(url_for('index'))
    return render_template('login.html')

@app.route('/signup', methods=['GET', 'POST'])
def signup():
    if request.method == 'POST':
        pw = generate_password_hash(request.form['password'])
        new_user = User(username=request.form['username'], password=pw)
        db.session.add(new_user)
        db.session.commit()
        return redirect(url_for('login'))
    return render_template('signup.html')

@app.route('/logout')
def logout():
    session.pop('user_id', None)
    return redirect(url_for('login'))

with app.app_context():
    db.create_all()
    os.makedirs(app.config['UPLOAD_FOLDER'], exist_ok=True)

if __name__ == '__main__':
    # Use the port Render provides or default to 10000
    port = int(os.environ.get("PORT", 10000))
    app.run(host='0.0.0.0', port=port)
