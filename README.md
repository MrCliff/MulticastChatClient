MulticastChatClient
===================

Yksinkertainen chat asiakas, joka käyttää multicastia viestien välitykseen.

Käyttö
------

Käännetyt class-tiedostot:\
`java MulticastChatClient [Multicast-osoite] [Multicast-portti] [Käyttäjänimi] [Syntymäajan vuosi] [Syntymäajan kuukausi] [Syntymäajan päivä]`

Valmiit jar-paketit (löytyy projektin kansiosta *jar*):\
`java -jar MulticastChatClient-v1.0.jar [Multicast-osoite] [Multicast-portti] [Käyttäjänimi] [Syntymäajan vuosi] [Syntymäajan kuukausi] [Syntymäajan päivä]`

Esimerkki
---------

`java MulticastChatClient 239.0.0.1 6666 "Test user" 1990 1 1` \
`java -jar MulticastChatClient-v1.0.jar 239.0.0.1 6666 "Test user" 1990 1 1`
