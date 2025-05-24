# P2P Chat App with end-to-end encryption

Conseguimos desenvolver a nossa aplicação da forma mais descentralizada possível,
permitindo apenas a comunicação de mensagens de forma direta entre utilizadores.

É possível a pesquisa por peers que existam na database, é possível visualizar as
conversas recentes no lado esquerdo da interface, onde também aparece o número de
mensagens não lidas de cada conversa.

É possível o envio de mensagens para qualquer user presente na database, e o
histórico de mensagens enviadas e recebidas é mantido e visualizável cada vez que a
conversa é selecionada.

É também mantido um storage de mensagens recebidas enquanto estamos offline, que
são carregadas e adicionadas ao histórico da conversa ao iniciar a app, bem como a
atualização do número de mensagens não lidas (Para esta feature, dada que não é
comunicação direta, pois o socket está offline e não permite ligação, utilizámos o server
como “cloud storage” temporário).

Group conversations: Criação de grupos, Juntar-se a grupos presentes na base de dados por tópico de interesse, 
enviar mensagens para grupos: Mensagens enviadas com ambos os users online são 100% peer-to-peer,
já as mensagens com um dos lados offline são guardadas no server até o outro user estar online.

Long-term storage of messages: Replicação da database entre servers para, no caso de um crashar, os
clientes conseguirem continuar as suas operações normalmente. Os servers
dispõem de crash recovery: quando voltam a estar online pedem ajuda aos
servers atualizados para que atualizem as suas DB’s.

Sistema de recuperação de Keystores e Truststores (Private Keys e Public
Keys): É utilizado Shamir Secret Sharing para enviar shares da private key aos
servers (Pelo menos metade dos servers têm de estar online no ato do
registo, pois a threshold para recuperação de shares é metade dos servers
presentes no serverAddresses.txt. Em caso de número ímpar de servers tem
de ser rounded up -> 5 servers equivale a 3 online).

Message Searching: Pesquisa de mensagens por uma ou mais keywords, independentemente de
ser lower ou upper case, independentemente da ordem das keywords,
retorna os resultados por número de aparições das palavras.
