1.
Pobierz JWT w Postmanie
Otwórz Postman → New Request.
Method: GET
URL: http://localhost:8080/auth/token?user=admin (możesz użyć user=dev albo innego)
Kliknij Send.
W odpowiedzi powinieneś dostać JSON: { "token": "eyJ..." }.
Skopiuj wartość token (bez cudzysłowów).



    "token": "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc3NDEyODA4MiwiZXhwIjoxNzc0MTMxNjgyfQ.3-Peh9j436WeOW8refRMAiBjw4cDMz6aNRJgr45PVyJ0wDHo9YYOEhuNq61HZQSG"


2.
Wyślij POST /api/transactions w Postmanie
New Request → Method: POST → URL: http://localhost:8080/api/transactions
Headers:
Key: Content-Type, Value: application/json
Key: Authorization, Value: Bearer <TU_WKLEJ_TOKEN> (z kroku 2)
Body:
Wybierz raw → JSON
Przykładowe body:


Bearer eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJhZG1pbiIsImlhdCI6MTc3NDEyODA4MiwiZXhwIjoxNzc0MTMxNjgyfQ.3-Peh9j436WeOW8refRMAiBjw4cDMz6aNRJgr45PVyJ0wDHo9YYOEhuNq61HZQSG