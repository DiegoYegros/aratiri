# Aratiri 🗲

A multi-user Bitcoin Lightning and on-chain middleware platform.

## Features
- Multi-user authentication
- LNURL invoice generation
- Internal/external Lightning routing
- On-chain settlement
- Pay to nostr npub and nip-05 addresses

## Getting Started
###  Requirements
* LND Node (mainnet or testnet)
* Java 21
* Docker (optional)
* Access to your node's admin macaroon and TLS cert.

### Setup
#### 1. Export your LND admin macaroon
Aratiri authenticates with your Lightning node using your admin macaroon.
Convert it to a hex string and save the output in 'secrets/admin.macaroon`:
```bash
  xxd -p ~/.lnd/data/chain/bitcoin/mainnet/admin.macaroon | tr -d '\n'
```
#### 2...
#### 3...