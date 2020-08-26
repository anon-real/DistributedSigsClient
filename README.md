# Zero-Knowledge Treasury on Top of [ERGO](https://ergoplatform.org/en/)
Client-side app for ZK Treasury implemented on top of ERGO's Distributed Signatures.

Each team member will have to run the client app in order for it to interact with the server, node and their secrets to generate necessary proofs.

## Configuration
Before running the client app you should do the configuration. You can find the `application.conf` in `conf/` folder.

* Specify the server url.
* Configure your node (url, api key), this info will only be used to generate necessary proofs for proposals that **you have approved**.
* Specify your public key.
* Specify your external secret if needed. This is only needed if the secret associated with your public key is external to the node's wallet.


## Running the code
### Development mode
To run the code in development run `sbt "run 9001"` in the main directory to start the client. Load the UI in http://localhost:9001.
replace 9001 with your desired port.
### TODO run in production and binaries

## Docker Quick Start
TODO

