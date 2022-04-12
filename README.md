# CSC445-Proj2-TFTP
Simple File Transfer Protocal based on TFTP
Differences from TFTP are:
 - Messages with OPCODE ERR are not sent in response to recieving a signal from another client, as I bind the server and client to each other
 - The string encoding type for the filename is not included in the RRQ/WRQ.
 - You can choose to randomly 'drop' 1% of the packets.

Website showing the results can be found at: http://cs.oswego.edu/~tkamerma/CSC445/Project%202/

Original Project Description:
# Assignment 2
Write a file transfer program. To demonstrate, you'll need a client and a server program:

  - The server awaits connections.
  - A client connects, and indicates the name of a file to upload or download.
  - The client sends or receives the file. 

Wherever applicable, use the commands and protocol for TFTP (IETF RFC 1350), with the following modifications. You will need to design and use additional packet header information than that in TFTP; use the IETF 2347 TFTP Options Extension when possible.

  - Use TCP-style sliding windows rather than the sequential acks used in TFTP. Test with at least two different max window sizes.
  - Arrange that each session begins with a (random) number exchange to generate a key that is used for encrypting data. You can just use Xor to create key, or anything better.
  - Support only binary (octet) transmission.
  - Support a command line argument controlling whether to randomly drop 1 percent of the packets; 

Create a web page showing throughput across varying conditions: at least 2 different pairs of hosts, different window sizes; drops vs no drops; 
