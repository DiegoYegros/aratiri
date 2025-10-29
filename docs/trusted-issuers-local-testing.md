# Local trusted issuer testing guide

This guide walks through setting up a fake OAuth issuer so you can exercise Aratiri's trusted issuer token exchange flow without depending on an external identity provider.

## Prerequisites

Install the Python packages that the helper scripts use:

```bash
python3 -m pip install --upgrade jwcrypto pyjwt
```

## 1. Generate an RSA key pair

Create a private/public RSA key pair that will represent your local issuer.

```bash
openssl genpkey -algorithm RSA -out rsa-private.pem -pkeyopt rsa_keygen_bits:2048
openssl rsa -in rsa-private.pem -pubout -out rsa-public.pem
```

## 2. Publish a JWKS document

Convert the public key into a JWKS (JSON Web Key Set) file and serve it over HTTP so that Aratiri can download the signing keys.

```bash
python3 - <<'PY'
from jwcrypto import jwk
key = jwk.JWK.from_pem(open("rsa-public.pem", "rb").read())
key["kid"] = "local-test"
open("jwks.json", "w").write('{"keys":[' + key.export(private_key=False) + "]}")
PY

python3 -m http.server 8000
```

The second command keeps running so that `http://localhost:8000/jwks.json` remains available.

## 3. Configure Aratiri

Set up the configuration so the app trusts the new issuer and enables the token exchange endpoint.

```yaml
aratiri:
  security:
    token-exchange:
      enabled: true
      client-id: tester
      client-secret: changeit
    trusted-issuers:
      - issuer: http://localhost:8000
        jwk-set-uri: http://localhost:8000/jwks.json
        principal-claim: email
        name-claim: name
        audience:
          - aratiri-local
        auto-provision-user: true
        auto-provision-account: false
```

## 4. Mint a test JWT

Sign a token with the private key you generated in step 1. Ensure the `iss`, `aud`, and other claims match the configuration you just added.

```bash
python3 - <<'PY'
import jwt, time
with open("rsa-private.pem", "rb") as fh:
    key = fh.read()
now = int(time.time())
token = jwt.encode(
    {
        "iss": "http://localhost:8000",
        "aud": ["aratiri-local"],
        "iat": now,
        "exp": now + 3600,
        "email": "alice@example.com",
        "name": "Alice Example"
    },
    key,
    algorithm="RS256",
    headers={"kid": "local-test"}
)
print(token)
PY
```

Copy the printed JWT; it becomes the `externalToken` for the exchange call.

## 5. Exercise the `/v1/auth/exchange` endpoint

Use Postman or curl to trade the external token for an Aratiri access token.

Encode the client-id:client-secret pair you configured in step 1 using Base64, so the header looks like `Authorization: Basic dGVzdGVyOmNoYW5nZWl0`.

```bash
curl -u tester:changeit \
  -H 'Content-Type: application/json' \
  -d '{"externalToken": "<paste token here>"}' \
  http://localhost:8080/v1/auth/exchange
```

A successful response returns Aratiri-issued access and refresh tokens. You can then call an authenticated endpoint (for example `GET /v1/auth/me`) using the new access token to verify the provisioning flow.

If the exchange fails, check Aratiri's logs for issuer, audience, or claim validation errors. Most issues stem from mismatched `iss`/`aud` values or using the wrong Python JWT library.


