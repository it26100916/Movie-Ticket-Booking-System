"""End-to-end smoke test for the Movie Ticket Booking API.
Run the app first (mvn spring-boot:run), then:  python3 scripts/smoke-test.py
"""
import http.cookiejar, json, sys, urllib.request, urllib.error, uuid, datetime

BASE = "http://localhost:8080"
PASSED, FAILED = 0, 0


class Client:
    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))

    def call(self, method, path, body=None):
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(BASE + path, data=data, method=method,
                                     headers={"Content-Type": "application/json"})
        try:
            with self.opener.open(req) as r:
                raw = r.read().decode()
                return r.status, (json.loads(raw) if raw else None)
        except urllib.error.HTTPError as e:
            raw = e.read().decode()
            try:
                return e.code, json.loads(raw)
            except Exception:
                return e.code, raw


def check(name, condition, detail=""):
    global PASSED, FAILED
    if condition:
        PASSED += 1
        print(f"  [PASS] {name}")
    else:
        FAILED += 1
        print(f"  [FAIL] {name} {detail}")


admin, customer, customer2 = Client(), Client(), Client()

print("== 1. Public READ endpoints")
s, movies = admin.call("GET", "/api/movies")
check("GET /api/movies returns seeded movies", s == 200 and len(movies) >= 6, f"status={s}")
s, shows = admin.call("GET", "/api/shows")
check("GET /api/shows returns upcoming showtimes", s == 200 and len(shows) > 0, f"status={s}")
s, theatres = admin.call("GET", "/api/theatres")
check("GET /api/theatres returns theatres", s == 200 and len(theatres) >= 1)
s, screens = admin.call("GET", "/api/screens")
check("GET /api/screens returns screens", s == 200 and len(screens) >= 3)
s, _ = admin.call("GET", "/api/auth/me")
check("GET /api/auth/me is 401 when logged out", s == 401)

print("== 2. Separate login methods and role protection")
s, body = customer.call("POST", "/api/auth/customer/login", {"email": "admin@cinema.lk", "password": "admin123"})
check("Admin account cannot use customer login", s == 400, f"status={s}")
s, body = admin.call("POST", "/api/auth/admin/login", {"email": "customer@cinema.lk", "password": "customer123"})
check("Customer account cannot use admin login", s == 400, f"status={s}")
s, body = admin.call("POST", "/api/auth/admin/login", {"email": "admin@cinema.lk", "password": "wrong"})
check("Wrong admin password rejected", s == 400)
s, body = admin.call("POST", "/api/auth/admin/login", {"email": "admin@cinema.lk", "password": "admin123"})
check("Admin login succeeds", s == 200 and body["role"] == "ADMIN", f"status={s} body={body}")
s, body = customer.call("POST", "/api/auth/customer/login", {"email": "customer@cinema.lk", "password": "customer123"})
check("Customer login succeeds", s == 200 and body["role"] == "CUSTOMER", f"status={s} body={body}")
s, _ = customer.call("GET", "/api/admin/customers")
check("Customer session blocked from admin API", s == 403, f"status={s}")
s, _ = Client().call("GET", "/api/customer/bookings")
check("Anonymous user blocked from customer API", s == 401, f"status={s}")

print("== 3. Member 2 - Movie CRUD (admin)")
title = "Test Movie " + uuid.uuid4().hex[:6]
movie_req = {"title": title, "genre": "Comedy", "language": "English", "durationMinutes": 100,
             "rating": "U", "description": "Test", "posterUrl": None, "releaseDate": "2026-10-01", "format": "IMAX"}
s, created = admin.call("POST", "/api/admin/movies", movie_req)
check("CREATE movie (IMAX subclass via factory)", s == 201 and created["format"] == "IMAX", f"status={s} {created}")
mid = created["id"] if s == 201 else None
s, got = admin.call("GET", f"/api/movies/{mid}")
check("READ movie by id", s == 200 and got["title"] == title)
movie_req["genre"] = "Action"
s, upd = admin.call("PUT", f"/api/admin/movies/{mid}", movie_req)
check("UPDATE movie genre", s == 200 and upd["genre"] == "Action", f"status={s}")
movie_req["format"] = "STANDARD"
s, body = admin.call("PUT", f"/api/admin/movies/{mid}", movie_req)
check("UPDATE rejects format change (subclass is fixed)", s == 400, f"status={s}")
s, body = admin.call("DELETE", f"/api/admin/movies/{mid}")
check("DELETE movie", s == 200, f"status={s} {body}")
s, body = customer.call("POST", "/api/admin/movies", movie_req)
check("Customer cannot create movies", s == 403)

print("== 4. Member 3 - Theatre & Screen CRUD")
s, th = admin.call("POST", "/api/admin/theatres", {"name": "Test Theatre", "city": "Kandy", "address": "x", "phone": None})
check("CREATE theatre", s == 201, f"status={s} {th}")
tid = th["id"] if s == 201 else None
s, sc = admin.call("POST", "/api/admin/screens", {"name": "VIP test", "theatreId": tid, "screenType": "VIP", "rowCount": 3, "seatsPerRow": 4})
check("CREATE VIP screen (subclass + multiplier)", s == 201 and sc["priceMultiplier"] in (1.8, 1.80), f"status={s} {sc}")
scid = sc["id"] if s == 201 else None
s, sc2 = admin.call("PUT", f"/api/admin/screens/{scid}", {"name": "VIP renamed", "theatreId": tid, "screenType": "VIP", "rowCount": 3, "seatsPerRow": 4})
check("UPDATE screen name", s == 200 and sc2["name"] == "VIP renamed", f"status={s}")
s, body = admin.call("DELETE", f"/api/admin/theatres/{tid}")
check("DELETE theatre blocked while screens exist", s == 400, f"status={s}")
s, body = admin.call("DELETE", f"/api/admin/screens/{scid}")
check("DELETE screen", s == 200, f"status={s}")
s, body = admin.call("DELETE", f"/api/admin/theatres/{tid}")
check("DELETE theatre after screens removed", s == 200, f"status={s}")

print("== 5. Member 4 - Showtime CRUD and rules")
future = (datetime.datetime.now() + datetime.timedelta(days=5)).replace(hour=10, minute=0, second=0, microsecond=0)
mid2 = movies[0]["id"]
screen_id = screens[0]["id"]
show_req = {"movieId": mid2, "screenId": screen_id, "startTime": future.isoformat(), "basePrice": 1500}
s, show = admin.call("POST", "/api/admin/shows", show_req)
check("CREATE showtime", s == 201, f"status={s} {show}")
new_id = show["id"] if s == 201 else None
s, body = admin.call("POST", "/api/admin/shows", show_req)
check("Overlapping showtime on same screen rejected", s == 400, f"status={s}")
s, body = admin.call("POST", "/api/admin/shows", {**show_req, "startTime": (datetime.datetime.now() - datetime.timedelta(days=1)).isoformat()})
check("Showtime in the past rejected", s == 400, f"status={s}")
if new_id:
    s, upd_show = admin.call("PUT", f"/api/admin/shows/{new_id}", {**show_req, "basePrice": 1600})
    check("UPDATE showtime base price", s == 200 and float(upd_show["basePrice"]) == 1600.0, f"status={s}")
    check("Pricing strategy chosen (matinee 10:00 -> 0.8x)", upd_show["pricingStrategy"] == "Matinee", f"got {upd_show.get('pricingStrategy')}")

print("== 6. Member 5 + 6 - Customer booking, payment and cancellation")
s, chosen = customer.call("GET", f"/api/shows/{new_id}")
check("GET showtime with seat map", s == 200 and chosen["totalSeats"] > 0)
s, body = customer.call("POST", "/api/customer/bookings", {"showId": new_id, "seatCodes": ["A1", "A2"]})
check("CREATE booking (2 seats)", s == 201 and body["status"] == "PENDING_PAYMENT", f"status={s} {body}")
bid = body["id"] if s == 201 else None
s, body = customer.call("POST", "/api/customer/bookings", {"showId": new_id, "seatCodes": ["A1"]})
check("Seat already held by another booking is rejected", s == 400 and "already taken" in body["message"], f"{s} {body}")
s, body = customer.call("POST", "/api/customer/bookings", {"showId": new_id, "seatCodes": ["Z9"]})
check("Invalid seat code rejected", s == 400, f"status={s}")
s, body = customer2.call("POST", "/api/auth/customer/register", {"fullName": "Test Buyer", "email": f"t{uuid.uuid4().hex[:6]}@x.com", "password": "secret123", "phone": None})
check("REGISTER new customer (CREATE)", s == 201, f"status={s} {body}")
email2 = body.get("email") if s == 201 else None
customer2.call("POST", "/api/auth/customer/login", {"email": email2, "password": "secret123"})
s, body = customer2.call("GET", f"/api/customer/bookings/{bid}")
check("Another customer cannot read this booking", s == 404, f"status={s}")
s, body = customer.call("PUT", f"/api/customer/bookings/{bid}", {"seatCodes": ["B3", "B4", "B5"]})
check("UPDATE booking seats (3 seats)", s == 200 and len(body["seatCodes"]) == 3, f"status={s} {body}")
s, body = customer.call("POST", "/api/customer/payments", {"bookingId": bid, "method": "CARD", "reference": "1234"})
check("Card payment with invalid number rejected", s == 400, f"status={s}")
s, before = customer.call("GET", "/api/customer/profile")
s, body = customer.call("POST", "/api/customer/payments", {"bookingId": bid, "method": "WALLET", "reference": "077123456"})
check("Wallet payment (polymorphic processor) succeeds", s == 201 and body["status"] == "SUCCESS", f"status={s} {body}")
s, body = customer.call("POST", "/api/customer/payments", {"bookingId": bid, "method": "WALLET", "reference": "077123456"})
check("Paying twice is rejected", s == 400)
s, b2 = customer.call("GET", f"/api/customer/bookings/{bid}")
check("Booking is CONFIRMED after payment", b2["status"] == "CONFIRMED", f"{b2}")
s, body = customer.call("PUT", f"/api/customer/bookings/{bid}", {"seatCodes": ["C1"]})
check("Confirmed booking cannot change seats", s == 400)
s, prof = customer.call("GET", "/api/customer/profile")
check("Loyalty points awarded (1 per seat)", s == 200 and prof["loyaltyPoints"] - before["loyaltyPoints"] == 3, f"{prof}")
s, body = customer.call("POST", "/api/customer/bookings", {"showId": new_id, "seatCodes": ["D1"]})
bid2 = body["id"] if s == 201 else None
s, body = customer.call("DELETE", f"/api/customer/bookings/{bid2}")
check("Cancel pending booking removes it", s == 200 and body["bookingStatus"] == "DELETED", f"{s} {body}")
s, body = customer.call("DELETE", f"/api/customer/bookings/{bid}")
check("Cancel confirmed booking 4 days early -> full refund", s == 200 and float(body["refundAmount"]) > 0, f"{s} {body}")
s, body = customer.call("GET", "/api/customer/payments")
check("Payment history shows REFUNDED", s == 200 and body and body[0]["status"] == "REFUNDED", f"{body}")

print("== 7. Member 6 - file handling and admin payment tools")
s, body = admin.call("POST", "/api/admin/files/import-movies", {})
check("Import movies file (duplicates skipped)", s == 200 and body["skipped"] >= 6, f"{s} {body}")
s, body = admin.call("POST", "/api/admin/files/import-users", {})
check("Import users file (duplicates skipped)", s == 200 and body["skipped"] >= 4, f"{s} {body}")
s, pays = admin.call("GET", "/api/admin/payments")
check("Admin sees all payments", s == 200)
refunded = [p for p in pays if p["status"] == "REFUNDED"]
if refunded:
    s, body = admin.call("DELETE", f"/api/admin/payments/{refunded[0]['id']}")
    check("Admin deletes refunded payment", s == 200, f"{s} {body}")

print("== 8. Member 1 - customer and admin management")
s, custs = admin.call("GET", "/api/admin/customers?q=kasun")
check("Admin searches customers", s == 200 and len(custs) >= 1)
s, body = customer.call("DELETE", "/api/customer/profile")
check("Customer with booking history cannot delete account", s == 400, f"{s} {body}")
s, body = customer2.call("DELETE", "/api/customer/profile")
check("Customer deletes own fresh account (no bookings)", s == 200, f"{s} {body}")
s, admins_list = admin.call("GET", "/api/admin/admins")
check("Admin list shows admins", s == 200 and len(admins_list) >= 2)
s, body = admin.call("POST", "/api/admin/admins", {"fullName": "New Staff", "email": f"s{uuid.uuid4().hex[:5]}@cinema.lk", "password": "staff999", "permissionLevel": "STAFF"})
check("Super admin creates an admin", s == 201, f"{s} {body}")

print("== 9. Cleanup of this run's test data (admin DELETE)")
s, body = admin.call("DELETE", f"/api/admin/bookings/{bid}")
check("Admin removes cancelled booking", s == 200, f"{s} {body}")
s, body = admin.call("DELETE", f"/api/admin/shows/{new_id}")
check("Admin deletes showtime", s == 200, f"{s} {body}")

print("== 10. Report export (file write)")
req = urllib.request.Request(BASE + "/api/admin/files/export-sales")
with admin.opener.open(req) as r:
    text = r.read().decode()
check("Sales report downloads as text", "SALES REPORT" in text and "Net sales" in text)

print(f"\nRESULT: {PASSED} passed, {FAILED} failed")
sys.exit(1 if FAILED else 0)
