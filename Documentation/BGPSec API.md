# BGPSec API

BGPSec Router Keys can be configured via the API. All endpoints use `application/json` request and response content, and
return HTTP 2xx status codes for successful requests.

## Router Keys

A BGPSec Router Key uses the following attributes:

- `asn`: ASN to be used in the generated router certificate
- `routerId`: BGP Identifier to be used in the generated router certificate
- `keyIdentifier`: hex-formatted SHA-1 hash of the public key identifier (case-insensitive)
- `csr`: PKCS#10 certification request (also known a the Certificate Signing Request) in ASN.1 form
- `routerKeyId`: unique identifier for a Router Key instance, created by the RPKI CA

The collection of BGPSec Router Keys is treated as a set, where their identity is defined by the tuple of `asn`,
`routerId`, and `keyIdentifier`. Therefore the same ASN may be used with different keys, and the same key may be reused
across different ASNs. Actual usage is subject to the caller’s operational requirements.

## List Router Keys

```http
GET /api/ca/{caId}/bgpsec
```

Returns all BGPSec Router Keys that are configured for the CA. Keys can be filtered thorugh query parameters on `asn`,
`keyIdentifier`, and `routerId`.

__Example response__

```json
{
  "routerKeys": [
    {
      "routerKeyId": 1,
      "asn": "AS0",
      "routerId": 0,
      "keyIdentifier": "0000000000000000000000000000000000000000",
      "csr": "...<base64 of the CSR>"
    }
  ]
}
```

## Create a new Router Key

Submit a BGPSec PKCS#10 (CSR) for issuance.

```http
POST /api/ca/{caId}/bgpsec
```

__Example request__

```json
{
  "asn": "AS0",
  "routerId": 0,
  "csr": "...<base64 of the CSR>"
}
```

__Example response__

``` json
{
  "routerKeyId": 1,
  "asn": "AS0",
  "routerId": 0,
  "keyIdentifier": "0000000000000000000000000000000000000000",
  "csr": "...<base64 of the CSR>"
}

```

## Get Single Router Key

Returns a single BGPSec Router Key by its `routerKeyId`.

```http
GET /api/ca/{caId}/bgpsec/{routerKeyId}
```

__Example response__

``` json
{
  "routerKeyId": 1,
  "asn": "AS0",
  "routerId": 0,
  "keyIdentifier": "0000000000000000000000000000000000000000",
  "csr": "...<base64 of the CSR>"
}
```

## Revoke

Revoke a specific BGPSec Router Key.

```http
DELETE /api/ca/{caId}/bgpsec/{routerKeyId}
```

A successful revocation request returns HTTP 204 with no response content.

## Get BGPSec EE certificate

```http
GET /api/ca/{caId}/bgpsec/{routerKeyId}/certificate
```

Returns the BGPSec router EE certificate in PKCS#7 certs-only format.

## Get BGPSec EE certificate chain

```http
GET /api/ca/{caId}/bgpsec/{routerKeyId}/chain
```

Returns the BGPSec router EE certificate and the full issuer chain in PKCS#7 certs-only format.
