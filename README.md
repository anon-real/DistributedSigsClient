# Zero-Knowledge Treasury on Top of [ERGO](https://ergoplatform.org/en/)
Client-side app for ZK Treasury implemented on top of ERGO's Distributed Signatures.

Each team member will have to run the client app in order for it to interact with the server, node and their secrets to generate necessary proofs.

## Configuration
Before running the client app you should do the configuration. You can find the `application.conf` in `conf/` folder [here](conf/application.conf).

* Specify the server url.
* Configure your node (url, api key), this info will only be used to generate necessary proofs for proposals that **you have approved**.
Your node must support distributed signatures (currently, distributed-sig branch or the node).
* Specify your public key.
* Specify your external secret if needed. This is only needed if the secret associated with your public key is external to the node's wallet.


## Running the code
### Development mode
To run the code in development run `sbt "run 9001"` in the main directory to start the client. Load the UI in http://localhost:9001.
replace 9001 with your desired port.
### Jar file
You can download the client's jar file [here](https://github.com/anon-real/DistributedSigsClient/releases). To run the client app:
```bash
java -jar -Dconfig.file="path/to/your/config/file" ZKTreasury-client-{version}.jar
```
If you want to run the client on different port also add `-Dhttp.port=8000` and replace 8000 with your desired port.

## Docker Quick Start
TODO

