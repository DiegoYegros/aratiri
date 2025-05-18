# Aratiri
A multi-user Bitcoin Lightning and on-chain middleware platform.

It handles authentication, invoice generation, on-chain and lightning routing, internal balance management and external settlement via a connected Lightning node.

# How to run

## Macaroon

Paste your macaroon from LND in the resources folder in a file named 'admin.macaroon'.

Example:

```sh
xxd -ps -u -c 1000 ~/.lnd/data/chain/bitcoin/mainnet/admin.macaroon
```