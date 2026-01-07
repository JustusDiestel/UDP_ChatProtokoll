# UDP_ChatProtokoll

Dies ist ein einfaches UDP-Chatprotokoll, das es Benutzern ermöglicht, Nachrichten über das UDP-Protokoll zu senden und zu empfangen. Das Protokoll unterstützt grundlegende Funktionen wie das Senden von Nachrichten, das Empfangen von Nachrichten und die Anzeige von Chat-Verläufen.

# Programm starten

mvn compile exec:java -Dexec.mainClass=net.p2pchat.Main -Dexec.args="5000"
mvn compile exec:java -Dexec.mainClass=net.p2pchat.Main -Dexec.args="5000 10.8.3.3"

! den Port 5000 durch den gewünschten Port ersetzen.


#Befehel
connect: connect 10.8.3.4 5000

msg: msg 10.8.3.4 5000

sendfile: sendfile 10.8.3.4 5000 


Frage
# checksum
	•	Checksum schützt nur Payload
	•	TTL wird nicht gehasht
