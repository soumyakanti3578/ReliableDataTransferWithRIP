Name: Soumyakanti Das
ID: sd3578

To build, run
$ sudo docker build -t node .

To run receiver:
$ sudo docker run -it --cap-add=NET_ADMIN --net nodenet --ip 172.18.0.21 rover 1

To run sender:
sudo docker run -it --cap-add=NET_ADMIN --net nodenet --ip 172.18.0.22 rover 2 <receiver_internal_ip> <filename>

Sender waits for 10 seconds to let the network settle down after the receiver appears in its next hop table.

The received file will have the name xyz-received.abc if the sending file is xyz.abc

The sender reads from the file in chunks of 100 MB (or less for the last chunk) and sends the chunk across to the receiver.
The receiver keeps appending the chunks to the received file. Connection closes only when the whole file is sent to the receiver.
