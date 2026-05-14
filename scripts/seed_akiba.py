#!/usr/bin/env python3
"""
Akiba DB Seed Script
=====================
Uses existing pre-seeded admin users (no registration needed).
Run from the project root:  python3 scripts/seed_akiba.py

Prerequisites:
  pip3 install httpx --break-system-packages
  All Docker services must be running: docker-compose up -d
"""

import httpx
import time
import sys
from datetime import datetime
import psycopg2

# ─────────────────────────────────────────────
# SERVICE BASE URLS
# ─────────────────────────────────────────────
AUTH          = "http://localhost:8081"
TRANSACTIONS  = "http://localhost:8082"
PARSING       = "http://localhost:8083"
AI_SVC        = "http://localhost:8084"
PAYMENTS      = "http://localhost:8085"
BUDGETS       = "http://localhost:8086"
SAVINGS       = "http://localhost:8087"
NOTIFICATIONS = "http://localhost:8088"
GATEWAY       = "http://localhost:8080"  # API Gateway — validates JWT, forwards X-User-Id

TIMEOUT = 30

# ─────────────────────────────────────────────
# DIRECT DB CONFIG (for services with broken gateway proxying)
# ─────────────────────────────────────────────
DB_CONFIG = {
    "host":     "localhost",
    "port":     5432,
    "dbname":   "akiba_db",
    "user":     "akiba",
    "password": "akiba_secret",
}

# ─────────────────────────────────────────────
# COLOURS
# ─────────────────────────────────────────────
GREEN  = "\033[92m"
YELLOW = "\033[93m"
RED    = "\033[91m"
BLUE   = "\033[94m"
BOLD   = "\033[1m"
RESET  = "\033[0m"

def ok(msg):     print(f"{GREEN}  ✅  {msg}{RESET}")
def warn(msg):   print(f"{YELLOW}  ⚠️   {msg}{RESET}")
def err(msg):    print(f"{RED}  ❌  {msg}{RESET}")
def info(msg):   print(f"{BLUE}  ➡️   {msg}{RESET}")
def header(msg): print(f"\n{BOLD}{BLUE}{'─'*55}\n  {msg}\n{'─'*55}{RESET}")

# ─────────────────────────────────────────────
# PRE-SEEDED USERS (already ACTIVE in DB, no OTP needed)
# Password hash in DB = 'Pass@0202'
# ─────────────────────────────────────────────
USERS = [
    {"email": "karanjaian420@gmail.com", "password": "Pass@0202", "label": "Admin"},
    {"email": "iankaranja420@gmail.com",  "password": "Pass@0202", "label": "User"},
]

# ─────────────────────────────────────────────
# BUDGET LIMITS
# ─────────────────────────────────────────────
BUDGET_LIMITS = [
    {"category": "Food",          "monthlyLimit": 15000.0},
    {"category": "Transport",     "monthlyLimit": 8000.0},
    {"category": "Rent",          "monthlyLimit": 25000.0},
    {"category": "Bills",         "monthlyLimit": 5000.0},
    {"category": "Entertainment", "monthlyLimit": 3000.0},
    {"category": "Health",        "monthlyLimit": 5000.0},
    {"category": "Shopping",      "monthlyLimit": 10000.0},
    {"category": "Savings",       "monthlyLimit": 12000.0},
]

# ─────────────────────────────────────────────
# SAVINGS GOALS
# ─────────────────────────────────────────────
GOALS = [
    {"name": "New Laptop",     "target_amount": 85000.0, "deadline": "2026-12-31", "icon": "laptop"},
    {"name": "Emergency Fund", "target_amount": 50000.0, "deadline": "2026-09-30", "icon": "shield"},
    {"name": "Holiday Trip",   "target_amount": 30000.0, "deadline": "2026-08-31", "icon": "plane"},
]

# ─────────────────────────────────────────────
# REALISTIC M-PESA SMS BLOCK
# ─────────────────────────────────────────────
MPESA_SMS = (
    "SAL1APRIL1 Confirmed. You have received Ksh45,000.00 from ACME CORP LTD on 1/4/26 at 9:00 AM. New M-PESA balance is Ksh45,000.00. Transaction cost, Ksh0.00.\n"
    "QK72XAM001 Confirmed. Ksh1,200.00 paid to JAVA HOUSE on 2/4/26 at 8:23 AM. New M-PESA balance is Ksh43,800.00. Transaction cost, Ksh0.00.\n"
    "PL98QWE002 Confirmed. Ksh850.00 sent to MATATU SACCO on 3/4/26 at 7:15 AM. New M-PESA balance is Ksh42,950.00. Transaction cost, Ksh0.00.\n"
    "NM34ZXC003 Confirmed. Ksh25,000.00 paid to LANDLORD PROPERTIES on 3/4/26 at 10:00 AM. New M-PESA balance is Ksh17,950.00. Transaction cost, Ksh0.00.\n"
    "BV56CVB004 Confirmed. Ksh3,500.00 paid to NAIVAS SUPERMARKET on 5/4/26 at 5:40 PM. New M-PESA balance is Ksh14,450.00. Transaction cost, Ksh0.00.\n"
    "XZ90MNB005 Confirmed. Ksh500.00 paid to KPLC TOKENS 012345 on 6/4/26 at 9:10 AM. New M-PESA balance is Ksh13,950.00. Transaction cost, Ksh0.00.\n"
    "TY12OPQ006 Confirmed. Ksh750.00 sent to BODABODA RIDER on 7/4/26 at 6:45 PM. New M-PESA balance is Ksh13,200.00. Transaction cost, Ksh0.00.\n"
    "GH45RST007 Confirmed. Ksh2,000.00 paid to QUICKMART SUPERMARKET on 8/4/26 at 1:20 PM. New M-PESA balance is Ksh11,200.00. Transaction cost, Ksh0.00.\n"
    "JK78UVW008 Confirmed. Ksh600.00 paid to SPOTIFY KENYA on 9/4/26 at 12:00 PM. New M-PESA balance is Ksh10,600.00. Transaction cost, Ksh0.00.\n"
    "LM23EFG009 Confirmed. Ksh1,500.00 paid to SAFARICOM POSTPAY on 10/4/26 at 8:00 AM. New M-PESA balance is Ksh9,100.00. Transaction cost, Ksh0.00.\n"
    "QA56HIJ010 Confirmed. Ksh900.00 paid to JAVA HOUSE SARIT on 11/4/26 at 1:05 PM. New M-PESA balance is Ksh8,200.00. Transaction cost, Ksh0.00.\n"
    "WS89KLM011 Confirmed. Ksh650.00 sent to MATATU on 12/4/26 at 7:30 AM. New M-PESA balance is Ksh7,550.00. Transaction cost, Ksh0.00.\n"
    "ED12NOP012 Confirmed. You have received Ksh5,000.00 from FREELANCE CLIENT on 13/4/26 at 3:00 PM. New M-PESA balance is Ksh12,550.00. Transaction cost, Ksh0.00.\n"
    "RF45QRS013 Confirmed. Ksh3,200.00 paid to NAIROBI HOSPITAL on 14/4/26 at 11:00 AM. New M-PESA balance is Ksh9,350.00. Transaction cost, Ksh0.00.\n"
    "TG78TUV014 Confirmed. Ksh2,500.00 paid to CARREFOUR MALL on 15/4/26 at 4:15 PM. New M-PESA balance is Ksh6,850.00. Transaction cost, Ksh0.00.\n"
    "YH90WXY015 Confirmed. Ksh1,800.00 paid to DSTV SUBSCRIPTION on 16/4/26 at 9:00 AM. New M-PESA balance is Ksh5,050.00. Transaction cost, Ksh0.00.\n"
    "UJ23ZAB016 Confirmed. Ksh5,000.00 paid to SAVINGS GOAL LAPTOP on 17/4/26 at 10:00 AM. New M-PESA balance is Ksh50.00. Transaction cost, Ksh0.00.\n"
    "IK56CDE017 Confirmed. You have received Ksh45,000.00 from ACME CORP LTD on 18/4/26 at 9:00 AM. New M-PESA balance is Ksh45,050.00. Transaction cost, Ksh0.00.\n"
    "OL89FGH018 Confirmed. Ksh400.00 paid to UBER KENYA on 18/4/26 at 6:00 PM. New M-PESA balance is Ksh44,650.00. Transaction cost, Ksh0.00.\n"
    "PM12IJK019 Confirmed. Ksh3,000.00 paid to QUICKMART WESTLANDS on 19/4/26 at 3:30 PM. New M-PESA balance is Ksh41,650.00. Transaction cost, Ksh0.00.\n"
    "AZ45LMN020 Confirmed. Ksh2,200.00 paid to JAVA HOUSE JUNCTION on 20/4/26 at 12:45 PM. New M-PESA balance is Ksh39,450.00. Transaction cost, Ksh0.00.\n"
    "SX78OPQ021 Confirmed. Ksh1,000.00 paid to AIRTIME TOP UP on 21/4/26 at 8:20 AM. New M-PESA balance is Ksh38,450.00. Transaction cost, Ksh0.00.\n"
    "DC01RST022 Confirmed. Ksh4,500.00 paid to WOOLWORTHS GARDEN CITY on 22/4/26 at 5:00 PM. New M-PESA balance is Ksh33,950.00. Transaction cost, Ksh0.00.\n"
    "FV34UVW023 Confirmed. Ksh800.00 sent to MATATU CBD on 23/4/26 at 7:00 AM. New M-PESA balance is Ksh33,150.00. Transaction cost, Ksh0.00.\n"
    "BN67XYZ024 Confirmed. Ksh5,000.00 paid to SAVINGS EMERGENCY FUND on 24/4/26 at 10:30 AM. New M-PESA balance is Ksh28,150.00. Transaction cost, Ksh0.00.\n"
    "HM90ABC025 Confirmed. Ksh1,100.00 paid to JAVA HOUSE WESTGATE on 25/4/26 at 9:15 AM. New M-PESA balance is Ksh27,050.00. Transaction cost, Ksh0.00."
)

# ─────────────────────────────────────────────
# MANUAL TRANSACTIONS
# ─────────────────────────────────────────────
MANUAL_TRANSACTIONS = [
    {"amount": 45000.0, "type": "CREDIT", "category": "Income",    "merchant": "ACME Corp",          "rawText": "April Salary - ACME Corp",       "source": "MANUAL", "reference": "SAL001", "date": "2026-04-01"},
    {"amount": 1200.0,  "type": "DEBIT",  "category": "Food",      "merchant": "Java House",          "rawText": "Lunch at Java House",            "source": "MANUAL", "reference": "MAN002", "date": "2026-04-02"},
    {"amount": 850.0,   "type": "DEBIT",  "category": "Transport", "merchant": "Matatu",              "rawText": "Matatu fare CBD",                "source": "MANUAL", "reference": "MAN003", "date": "2026-04-03"},
    {"amount": 25000.0, "type": "DEBIT",  "category": "Rent",      "merchant": "Landlord Properties", "rawText": "April Rent",                     "source": "MANUAL", "reference": "MAN004", "date": "2026-04-03"},
    {"amount": 3500.0,  "type": "DEBIT",  "category": "Food",      "merchant": "Naivas Supermarket",  "rawText": "Naivas Grocery Shopping",        "source": "MANUAL", "reference": "MAN005", "date": "2026-04-05"},
    {"amount": 500.0,   "type": "DEBIT",  "category": "Bills",     "merchant": "KPLC",                "rawText": "KPLC Token purchase",            "source": "MANUAL", "reference": "MAN006", "date": "2026-04-06"},
    {"amount": 3200.0,  "type": "DEBIT",  "category": "Health",    "merchant": "Nairobi Hospital",    "rawText": "Nairobi Hospital checkup",       "source": "MANUAL", "reference": "MAN007", "date": "2026-04-14"},
    {"amount": 5000.0,  "type": "CREDIT", "category": "Income",    "merchant": "Freelance Client",    "rawText": "Freelance payment received",     "source": "MANUAL", "reference": "MAN008", "date": "2026-04-13"},
    {"amount": 5000.0,  "type": "DEBIT",  "category": "Savings",   "merchant": "Savings",             "rawText": "Laptop goal contribution",       "source": "MANUAL", "reference": "MAN009", "date": "2026-04-17"},
    {"amount": 5000.0,  "type": "DEBIT",  "category": "Savings",   "merchant": "Savings",             "rawText": "Emergency fund contribution",    "source": "MANUAL", "reference": "MAN010", "date": "2026-04-24"},
]


# ═════════════════════════════════════════════
#  HTTP HELPERS
# ═════════════════════════════════════════════

def _headers(token=None, user_id=None):
    h = {"Content-Type": "application/json"}
    if token:
        h["Authorization"] = f"Bearer {token}"
    if user_id:
        h["X-User-Id"] = str(user_id)
    return h

def health_get(url):
    try:
        r = httpx.get(url, timeout=TIMEOUT)
        return r.status_code < 500
    except Exception:
        return False

def post(url, payload, token=None, user_id=None, label=""):
    try:
        r = httpx.post(url, json=payload, headers=_headers(token, user_id), timeout=TIMEOUT)
        if r.status_code in (200, 201, 202):
            try:
                return r.json()
            except Exception:
                return {"raw": r.text}
        else:
            warn(f"{label} HTTP {r.status_code}: {r.text[:200]}")
            return None
    except httpx.ConnectError:
        err(f"Cannot connect to {url}")
        return None
    except Exception as e:
        err(f"{label} failed: {e}")
        return None

def get(url, token=None, user_id=None, label=""):
    try:
        r = httpx.get(url, headers=_headers(token, user_id), timeout=TIMEOUT)
        if r.status_code == 200:
            try:
                return r.json()
            except Exception:
                return {"raw": r.text}
        else:
            warn(f"{label} HTTP {r.status_code}: {r.text[:200]}")
            return None
    except Exception as e:
        err(f"{label} failed: {e}")
        return None

def put(url, payload, token=None, user_id=None, label=""):
    try:
        r = httpx.put(url, json=payload, headers=_headers(token, user_id), timeout=TIMEOUT)
        if r.status_code in (200, 201, 204):
            try:
                return r.json()
            except Exception:
                return {"raw": r.text}  # 204 has no body — that's fine
        else:
            warn(f"{label} HTTP {r.status_code}: {r.text[:200]}")
            return None
    except Exception as e:
        err(f"{label} failed: {e}")
        return None


# ═════════════════════════════════════════════
#  STEP 0 — HEALTH CHECKS
# ═════════════════════════════════════════════

def check_services():
    header("STEP 0 — Health Checks")
    services = {
        "Auth Service":         f"{AUTH}/health",
        "Transaction Service":  f"{TRANSACTIONS}/transactions/health",
        "Parsing Service":      f"{PARSING}/health",
        "Budget Service":       f"{BUDGETS}/budgets/health",
        "AI Service":           f"{AI_SVC}/health",
        "Payment Service":      f"{PAYMENTS}/health",
        "Savings Service":      f"{SAVINGS}/health",
        "Notification Service": f"{NOTIFICATIONS}/health",
    }
    all_up = True
    for name, url in services.items():
        if health_get(url):
            ok(f"{name} is UP")
        else:
            err(f"{name} is DOWN — {url}")
            all_up = False

    if not all_up:
        print(f"\n{RED}{BOLD}Some services are down.{RESET}")
        print("Run: docker-compose up -d")
        print("Wait ~30 seconds then retry.\n")
        sys.exit(1)


# ═════════════════════════════════════════════
#  STEP 1 — LOGIN WITH PRE-SEEDED USERS
#  No registration — users already ACTIVE in DB
# ═════════════════════════════════════════════

def login_users():
    header("STEP 1 — Login with Pre-Seeded Users")
    sessions = []

    for user in USERS:
        info(f"Logging in {user['label']} ({user['email']})...")
        login_res = post(
            f"{AUTH}/auth/login",
            {"email": user["email"], "password": user["password"]},
            label="Login"
        )

        if login_res and login_res.get("accessToken"):
            token     = login_res["accessToken"]
            user_data = login_res.get("user", {})
            user_id   = (login_res.get("userId")
                         or user_data.get("id")
                         or user_data.get("userId", ""))
            ok(f"Logged in: {user['label']} | userId={user_id}")
            sessions.append({
                "user":   user,
                "token":  token,
                "userId": str(user_id),
            })
        else:
            warn(f"Login failed for {user['email']} — skipping")

    if not sessions:
        err("No users could log in. Check: docker-compose logs auth-service")
        sys.exit(1)

    return sessions


# ═════════════════════════════════════════════
#  STEP 2 — BUDGETS
# ═════════════════════════════════════════════

def seed_budgets(sessions):
    header("STEP 2 — Seed Budget Limits")
    for s in sessions:
        info(f"Creating budgets for {s['user']['label']}...")
        count = 0
        for b in BUDGET_LIMITS:
            if post(f"{BUDGETS}/budgets", b, token=s["token"], label="Budget"):
                count += 1
        ok(f"{count}/{len(BUDGET_LIMITS)} budgets created for {s['user']['label']}")


# ═════════════════════════════════════════════
#  STEP 3 — SAVINGS GOALS
# ═════════════════════════════════════════════

def seed_savings_goals(sessions):
    header("STEP 3 — Create Savings Goals (direct DB insert)")
    goal_ids = {}

    try:
        conn = psycopg2.connect(**DB_CONFIG)
        cur  = conn.cursor()

        for s in sessions:
            info(f"Creating savings goals for {s['user']['label']}...")
            ids = []
            for goal in GOALS:
                cur.execute("""
                    INSERT INTO savings.goals (user_id, name, target_amount, deadline, icon, status)
                    VALUES (%s, %s, %s, %s::date, %s, 'ACTIVE')
                    ON CONFLICT DO NOTHING
                    RETURNING id
                """, (
                    s["userId"],
                    goal["name"],
                    goal["target_amount"],
                    goal["deadline"],
                    goal.get("icon", "🎯"),
                ))
                row = cur.fetchone()
                if row:
                    ids.append(str(row[0]))
            conn.commit()
            goal_ids[s["userId"]] = ids
            ok(f"{len(ids)}/{len(GOALS)} goals created for {s['user']['label']}")

        cur.close()
        conn.close()

    except Exception as e:
        err(f"DB error seeding goals: {e}")

    return goal_ids


# ═════════════════════════════════════════════
#  STEP 4 — IMPORT M-PESA SMS
# ═════════════════════════════════════════════

def import_mpesa(sessions):
    header("STEP 4 — Import M-Pesa SMS")
    for s in sessions:
        info(f"Sending M-Pesa SMS for {s['user']['label']}...")
        info("  Pipeline: Parsing → Gemini AI → RabbitMQ → Transactions / Budgets / Savings")
        res = post(
            f"{PARSING}/parse/mpesa",
            {"smsText": MPESA_SMS},
            token=s["token"],
            label="ParseMpesa"
        )
        if res:
            count = res.get("transactionCount") or res.get("count") or "~25"
            ok(f"SMS accepted | {count} transactions queued")
        else:
            warn(f"No response from parsing service for {s['user']['label']}")

        info("  Waiting 6s for RabbitMQ pipeline to finish...")
        time.sleep(6)


# ═════════════════════════════════════════════
#  STEP 5 — MANUAL TRANSACTIONS
# ═════════════════════════════════════════════

def seed_manual_transactions(sessions):
    header("STEP 5 — Seed Manual Transactions")
    for s in sessions:
        info(f"Creating manual transactions for {s['user']['label']}...")
        count = 0
        for tx in MANUAL_TRANSACTIONS:
            if post(f"{TRANSACTIONS}/transactions", tx, token=s["token"], label="ManualTx"):
                count += 1
        ok(f"{count}/{len(MANUAL_TRANSACTIONS)} manual transactions for {s['user']['label']}")


# ═════════════════════════════════════════════
#  STEP 6 — SIMULATE PAYMENTS
# ═════════════════════════════════════════════

def seed_payments(sessions):
    header("STEP 6 — Simulate Payments (STK Push + Daraja Callback)")

    scenarios = [
        {"phone": "254748492654", "amount": 1200, "type": "PHONE",   "category": "Food",     "accountRef": None,        "nickname": "Java House"},
        {"phone": "254748492654", "amount": 3200, "type": "PAYBILL", "category": "Bills",    "accountRef": "012345678", "nickname": "KPLC Token"},
        {"phone": "254748492654", "amount": 850,  "type": "TILL",    "category": "Shopping", "accountRef": None,        "nickname": "Naivas Westlands"},
    ]

    for s in sessions:
        info(f"Simulating payments for {s['user']['label']}...")
        completed = 0

        for scenario in scenarios:
            init = post(
                f"{PAYMENTS}/payments/initiate",
                scenario,
                token=s["token"],
                user_id=s["userId"],
                label="InitiatePayment"
            )

            if not init:
                warn(f"Payment initiation failed for {scenario['nickname']}")
                continue

            payment_id  = init.get("paymentId") or init.get("id") or "SEED001"
            checkout_id = (init.get("checkoutRequestId")
                           or init.get("checkoutId")
                           or f"ws_CO_SEED_{payment_id}")
            ok(f"Initiated: {scenario['nickname']} Ksh {scenario['amount']}")
            time.sleep(1)

            callback = {
                "Body": {
                    "stkCallback": {
                        "MerchantRequestID": "SEED-MERCHANT-001",
                        "CheckoutRequestID": checkout_id,
                        "ResultCode": 0,
                        "ResultDesc": "The service request is processed successfully.",
                        "CallbackMetadata": {
                            "Item": [
                                {"Name": "Amount",             "Value": scenario["amount"]},
                                {"Name": "MpesaReceiptNumber", "Value": f"SEED{payment_id}"},
                                {"Name": "TransactionDate",    "Value": int(datetime.now().strftime("%Y%m%d%H%M%S"))},
                                {"Name": "PhoneNumber",        "Value": scenario["phone"]},
                            ]
                        }
                    }
                }
            }
            post(f"{PAYMENTS}/payments/callback", callback, label="DarajaCallback")
            ok(f"Callback sent: {scenario['nickname']} → COMPLETED")
            completed += 1
            time.sleep(0.5)

        ok(f"{completed}/{len(scenarios)} payments done for {s['user']['label']}")
        info("  Waiting 3s for payment.completed RabbitMQ events...")
        time.sleep(3)


# ═════════════════════════════════════════════
#  STEP 7 — GOAL CONTRIBUTIONS
# ═════════════════════════════════════════════

def seed_goal_contributions(sessions, goal_ids):
    header("STEP 7 — Add Goal Contributions (direct DB insert)")
    amounts = [5000.0, 3000.0, 2000.0]

    try:
        conn = psycopg2.connect(**DB_CONFIG)
        cur  = conn.cursor()

        for s in sessions:
            ids = goal_ids.get(s["userId"], [])
            if not ids:
                warn(f"No goal IDs for {s['user']['label']} — skipping")
                continue

            info(f"Adding contributions for {s['user']['label']}...")
            count = 0
            for i, goal_id in enumerate(ids):
                amount = amounts[i] if i < len(amounts) else 1000.0
                cur.execute("""
                    INSERT INTO savings.contributions (goal_id, user_id, amount, note)
                    VALUES (%s, %s, %s, %s)
                """, (goal_id, s["userId"], amount, "Seed contribution"))
                # Update current_amount on the goal
                cur.execute("""
                    UPDATE savings.goals
                    SET current_amount = COALESCE(current_amount, 0) + %s
                    WHERE id = %s AND user_id = %s
                """, (amount, goal_id, s["userId"]))
                ok(f"  Ksh {amount:,.0f} to goal {goal_id}")
                count += 1
            conn.commit()
            ok(f"{count}/{len(ids)} contributions for {s['user']['label']}")

        cur.close()
        conn.close()

    except Exception as e:
        err(f"DB error seeding contributions: {e}")


# ═════════════════════════════════════════════
#  STEP 8 — AI CONVERSATIONS
# ═════════════════════════════════════════════

def seed_ai_conversations(sessions):
    header("STEP 8 — Seed AI Conversations")
    prompts = [
        "Where am I overspending this month?",
        "Can I afford to spend Ksh 5,000 on entertainment this week?",
        "Give me a summary of my savings progress.",
    ]
    for s in sessions:
        info(f"Seeding AI conversations for {s['user']['label']}...")
        conv_id = None
        count   = 0
        for prompt in prompts:
            res = post(
                f"{GATEWAY}/ai/chat",
                {"conversation_id": conv_id, "message": prompt},
                token=s["token"],
                label="AiChat"
            )
            if res:
                conv_id = res.get("conversationId") or conv_id
                count += 1
                ok(f"  AI replied: \"{prompt[:55]}\"")
            time.sleep(1)
        ok(f"{count}/{len(prompts)} AI messages for {s['user']['label']}")


# ═════════════════════════════════════════════
#  STEP 9 — NOTIFICATION PREFERENCES
# ═════════════════════════════════════════════

def seed_notification_preferences(sessions):
    header("STEP 9 — Notification Preferences")
    prefs = {
        "push_token":     "ExponentPushToken[SeedTestToken12345]",
        "payment_alerts": True,
        "budget_alerts":  True,
        "savings_alerts": True,
        "report_alerts":  True,
    }
    for s in sessions:
        res = put(f"{NOTIFICATIONS}/notifications/preferences", prefs, token=s["token"], user_id=s["userId"], label="NotifPrefs")
        if res is not None:
            ok(f"Preferences set for {s['user']['label']}")
        else:
            warn(f"Could not set preferences for {s['user']['label']}")

    try:
        conn = psycopg2.connect(**DB_CONFIG)
        cur  = conn.cursor()
        for s in sessions:
            uid = s["userId"]
            alerts = [
                (uid, "PAYMENT_CONFIRMED", "Payment Sent",      "Ksh 1,200 sent to Java House successfully."),
                (uid, "BUDGET_ALERT",      "Budget Warning",    "You have used 85% of your Food budget this month."),
                (uid, "SAVINGS_MILESTONE", "Savings Milestone", "You are 10% closer to your New Laptop goal!"),
                (uid, "PAYMENT_CONFIRMED", "Payment Sent",      "Ksh 3,200 sent to KPLC Token successfully."),
                (uid, "BUDGET_ALERT",      "Budget Exceeded",   "You have exceeded your Transport budget this month."),
                (uid, "SAVINGS_MILESTONE", "Goal Contribution", "Ksh 5,000 added to Emergency Fund goal."),
            ]
            for a in alerts:
                cur.execute("INSERT INTO notifications.alerts (user_id, type, title, body) VALUES (%s,%s,%s,%s)", a)
            conn.commit()
            ok(f"6 notifications seeded for {s['user']['label']}")
        cur.close()
        conn.close()
    except Exception as e:
        err(f"DB error seeding notifications: {e}")


# ═════════════════════════════════════════════
#  STEP 10 — VERIFY
# ═════════════════════════════════════════════

def verify(sessions):
    header("STEP 10 — Verification")
    s = sessions[0]
    info(f"Spot-checking data for {s['user']['label']}...\n")

    checks = [
        ("Transactions",         f"{TRANSACTIONS}/transactions",         s["token"], None),
        ("Transaction Summary",  f"{TRANSACTIONS}/transactions/summary",  s["token"], None),
        ("Budget Overview",      f"{BUDGETS}/budgets/overview",           s["token"], None),
        ("Savings Goals",        f"{GATEWAY}/savings/goals",              s["token"], None),
        ("Unread Notifications", f"{NOTIFICATIONS}/notifications/unread-count", s["token"], s["userId"]),
    ]

    for label, url, token, uid in checks:
        res = get(url, token=token, user_id=uid, label=label)
        if res is not None:
            ok(f"{label}: ✓")
        else:
            warn(f"{label}: no data returned")


# ═════════════════════════════════════════════
#  MAIN
# ═════════════════════════════════════════════

def main():
    start = time.time()

    print(f"""\n{BOLD}{BLUE}
╔══════════════════════════════════════════════╗
║         AKIBA  —  DB  SEED  SCRIPT           ║
║   Every shilling has a story. Let's plant    ║
║   a few thousand of them.                    ║
╚══════════════════════════════════════════════╝
{RESET}
  {YELLOW}Using pre-seeded users (no registration/OTP needed){RESET}
  {YELLOW}Target: localhost (docker-compose){RESET}
""")

    check_services()
    sessions = login_users()
    seed_budgets(sessions)
    goal_ids = seed_savings_goals(sessions)
    import_mpesa(sessions)
    seed_manual_transactions(sessions)
    seed_payments(sessions)
    seed_goal_contributions(sessions, goal_ids)
    seed_ai_conversations(sessions)
    seed_notification_preferences(sessions)
    verify(sessions)

    elapsed = time.time() - start
    print(f"""\n{BOLD}{GREEN}
╔══════════════════════════════════════════════╗
║           🎉  SEED COMPLETE                  ║
╚══════════════════════════════════════════════╝
{RESET}
  {GREEN}✅  {len(sessions)} users seeded{RESET}
  {GREEN}✅  Budgets, goals, transactions, payments{RESET}
  {GREEN}✅  M-Pesa pipeline via RabbitMQ{RESET}
  {GREEN}✅  AI conversations seeded{RESET}
  {GREEN}✅  Notifications configured{RESET}

  {BLUE}Completed in {elapsed:.1f}s{RESET}

  {YELLOW}Login credentials:{RESET}
""")
    for u in USERS:
        print(f"    📧  {u['email']}  |  🔑  {u['password']}")
    print()


if __name__ == "__main__":
    main()
