# OAuth2 Custom Grant — Custom Error Code Demo

Demonstrates how to return **custom error codes** from a custom OAuth2 grant handler in WSO2 Identity Server 6.1.0.

When `validateGrant()` throws `IdentityOAuth2Exception(errorCode, message)`, the `errorCode` replaces the default `invalid_grant` in the token error response:

```json
{"error": "<errorCode>", "error_description": "<message>"}
```

## Custom Error Codes

| Scenario | Error Code | Trigger |
|----------|-----------|---------|
| Missing mobile number | `MOBILE_NUMBER_MISSING` | No `mobileNumber` param in request |
| Invalid format | `INVALID_MOBILE_FORMAT` | `mobileNumber` doesn't match 10-digit pattern |
| No matching user | `USER_NOT_FOUND` | Valid format but no user has that mobile number |
| User store error | `USER_STORE_ERROR` | Internal error during user store lookup |

## How It Works

The custom grant handler (`MobileGrant.java`) overrides `validateGrant()` and throws `IdentityOAuth2Exception` with a custom error code:

```java
throw new IdentityOAuth2Exception("INVALID_MOBILE_FORMAT",
        "The provided mobile number format is invalid.");
```

The `AccessTokenIssuer` in WSO2 IS catches this exception, extracts the error code via `e.getErrorCode()`, and uses it in the OAuth2 error response instead of the default `invalid_grant`.

### Important: Constructor Matters

| Constructor | Error Code in Response |
|---|---|
| `new IdentityOAuth2Exception("MY_CODE", "message")` | `"error": "MY_CODE"` |
| `new IdentityOAuth2Exception("MY_CODE", "message", e)` | `"error": "MY_CODE"` |
| `new IdentityOAuth2Exception("message", e)` | `"error": "invalid_grant"` (code NOT set) |
| `new IdentityOAuth2Exception("message")` | `"error": "invalid_grant"` (code NOT set) |

The error code must be the **first** parameter.

## Prerequisites

- WSO2 Identity Server 6.1.0
- Java 17
- Maven 3.x

## Build

```bash
mvn clean package -DskipTests
```

## Deploy

1. Copy the JAR to the IS server:

   ```bash
   cp target/custom-grant-1.0.0.jar <IS_HOME>/repository/components/lib/
   ```

2. Add the custom grant type configuration in `<IS_HOME>/repository/conf/deployment.toml`:

   ```toml
   [[oauth.custom_grant_type]]
   name = "mobile"
   grant_handler = "org.wso2.sample.identity.oauth2.grant.mobile.MobileGrant"
   grant_validator = "org.wso2.sample.identity.oauth2.grant.mobile.MobileGrantValidator"
   [oauth.custom_grant_type.properties]
   IdTokenAllowed = true
   ```

3. Start (or restart) the server:

   ```bash
   <IS_HOME>/bin/wso2server.sh start
   ```

## Test

### Step 1 — Register an OAuth app with the mobile grant type

```bash
curl -sk -X POST https://localhost:9443/api/identity/oauth2/dcr/v1.1/register \
  -H "Authorization: Basic <base64(username:password)>" \
  -H "Content-Type: application/json" \
  -d '{"client_name":"mobile_grant_test","grant_types":["mobile","client_credentials"],"redirect_uris":["https://localhost/callback"]}'
```

Note the `client_id` and `client_secret` from the response.

### Step 2 — Set a mobile number on the admin user's profile

```bash
curl -sk -X PATCH https://localhost:9443/scim2/Me \
  -H "Authorization: Basic <base64(username:password)>" \
  -H "Content-Type: application/json" \
  -d '{"schemas":["urn:ietf:params:scim:api:messages:2.0:PatchOp"],"Operations":[{"op":"replace","value":{"phoneNumbers":[{"type":"mobile","value":"0771234567"}]}}]}'
```

### Step 3 — Test: Invalid mobile number format (custom error code)

```bash
curl -sk -X POST https://localhost:9443/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -u "<client_id>:<client_secret>" \
  -d "grant_type=mobile&mobileNumber=12345"
```

**Expected response:**

```json
{"error_description":"The provided mobile number format is invalid.","error":"INVALID_MOBILE_FORMAT"}
```

### Step 4 — Test: No user found with mobile number (custom error code)

```bash
curl -sk -X POST https://localhost:9443/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -u "<client_id>:<client_secret>" \
  -d "grant_type=mobile&mobileNumber=0009999999"
```

**Expected response:**

```json
{"error_description":"No user found with the provided mobile number.","error":"USER_NOT_FOUND"}
```

### Step 5 — Test: Valid mobile number (success — token issued)

```bash
curl -sk -X POST https://localhost:9443/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -u "<client_id>:<client_secret>" \
  -d "grant_type=mobile&mobileNumber=0771234567"
```

**Expected response:**

```json
{"access_token":"...","refresh_token":"...","token_type":"Bearer","expires_in":3600}
```

### Step 6 — Test: Missing mobile number (caught by grant validator)

```bash
curl -sk -X POST https://localhost:9443/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -u "<client_id>:<client_secret>" \
  -d "grant_type=mobile"
```

**Expected response:**

```json
{"error_description":"Missing parameters: mobileNumber","error":"invalid_request"}
```

## Reference

- [IdentityOAuth2Exception.java](https://github.com/wso2-extensions/identity-inbound-auth-oauth/blob/v6.11.21/components/org.wso2.carbon.identity.oauth/src/main/java/org/wso2/carbon/identity/oauth2/IdentityOAuth2Exception.java)
- [AccessTokenIssuer.java](https://github.com/wso2-extensions/identity-inbound-auth-oauth/blob/v6.11.21/components/org.wso2.carbon.identity.oauth/src/main/java/org/wso2/carbon/identity/oauth2/token/AccessTokenIssuer.java)
