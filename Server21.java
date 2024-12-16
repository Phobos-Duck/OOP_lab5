import java.io.*;
import java.net.*;
import java.util.*;

class Server21 {
    private static final int PORT = 8030;
    private static final Map<String, Integer> CARD_VALUES = new HashMap<>();
    private static final List<String> DECK = new ArrayList<>();
    private static final Random RANDOM = new Random();

    private static final List<PlayerHandler> players = Collections.synchronizedList(new ArrayList<>());
    private static int currentPlayerIndex = 0;

    static {
        CARD_VALUES.put("2", 2);
        CARD_VALUES.put("3", 3);
        CARD_VALUES.put("4", 4);
        CARD_VALUES.put("5", 5);
        CARD_VALUES.put("6", 6);
        CARD_VALUES.put("7", 7);
        CARD_VALUES.put("8", 8);
        CARD_VALUES.put("9", 9);
        CARD_VALUES.put("10", 10);
        CARD_VALUES.put("J", 10);
        CARD_VALUES.put("Q", 10);
        CARD_VALUES.put("K", 10);
        CARD_VALUES.put("A", 11);

        DECK.addAll(CARD_VALUES.keySet());
    }

    public static void main(String[] args) throws IOException {
        System.out.println("Сервер запущен и готов к игре...");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket playerSocket = serverSocket.accept();
                System.out.println("Игрок подключился к серверу.");
                PlayerHandler playerHandler = new PlayerHandler(playerSocket, players.size() + 1);
                players.add(playerHandler);
                new Thread(playerHandler).start();

                if (players.size() == 2) {
                    startGame();
                }
            }
        }
    }

    private static void startGame() {
        synchronized (players) {
            for (PlayerHandler player : players) {
                player.sendMessage("Игра началась! Ожидайте своей очереди.");
            }
            players.get(currentPlayerIndex).notifyTurn();
        }
    }

    private static void endGame(String winnerMessage) {
        synchronized (players) {
            for (PlayerHandler player : players) {
                player.sendMessage(winnerMessage);
                player.disconnectPlayer();
            }
            players.clear();
        }
    }

    private static class PlayerHandler implements Runnable {
        private final Socket socket;
        private final PrintWriter out;
        private final BufferedReader in;
        private final List<String> playerCards = new ArrayList<>();
        private final int playerId;
        private boolean isMyTurn = false;
        private boolean hasStopped = false;

        public PlayerHandler(Socket socket, int playerId) throws IOException {
            this.socket = socket;
            this.playerId = playerId;
            this.out = new PrintWriter(socket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        }

        @Override
        public void run() {
            try {
                sendMessage("Добро пожаловать в игру 21! Вы - Игрок " + playerId + ". Ожидаем второго игрока...");

                while (true) {
                    synchronized (this) {
                        while (!isMyTurn) {
                            wait();
                        }
                    }

                    if (hasStopped) {
                        endTurn();
                        continue;
                    }

                    playTurn();
                    if (!hasStopped) {
                        endTurn();
                    }
                }
            } catch (IOException | InterruptedException e) {
                System.err.println("Ошибка при обработке игрока: " + e.getMessage());
            } finally {
                disconnectPlayer();
                players.remove(this);
                System.out.println("Игрок отключен.");
            }
        }

        private void playTurn() throws IOException {
            if (playerCards.isEmpty()) {
                playerCards.add(drawCard());
                sendMessage("Ваша первая карта: " + playerCards.get(0));
            }

            sendMessage("Ваши карты: " + playerCards);
            sendMessage("Ваш текущий счет: " + calculateScore(playerCards));
            sendMessage("Введите: 'взять', чтобы взять карту, или 'стоп', чтобы закончить.");

            String response = in.readLine();
            if ("взять".equalsIgnoreCase(response)) {
                String card = drawCard();
                playerCards.add(card);
                int score = calculateScore(playerCards);
                sendMessage("Вы взяли карту: " + card);
                sendMessage("Ваши карты: " + playerCards);
                sendMessage("Ваш текущий счет: " + score);

                if (score > 21) {
                    sendMessage("Перебор! Ваш счет: " + score + ". Вы проиграли.");
                    endGame("Игрок " + playerId + " перебрал. Победитель: Игрок " + getOtherPlayerId() + "!");
                }
            } else if ("стоп".equalsIgnoreCase(response)) {
                hasStopped = true;
                sendMessage("Вы закончили игру с итоговым счетом: " + calculateScore(playerCards) + ". Ожидайте окончания игры.");
                checkGameOver();
            }
        }

        private void endTurn() {
            synchronized (players) {
                isMyTurn = false;
                currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
                players.get(currentPlayerIndex).notifyTurn();
            }
        }

        private synchronized void notifyTurn() {
            isMyTurn = true;
            sendMessage("Ваш ход!");
            notify();
        }

        private void sendMessage(String message) {
            out.println(message);
        }

        private void disconnectPlayer() {
            try {
                sendMessage("Игра завершена. Вы будете отключены.");
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private String drawCard() {
            return DECK.get(RANDOM.nextInt(DECK.size()));
        }

        private int calculateScore(List<String> cards) {
            int score = 0;
            int aceCount = 0;

            for (String card : cards) {
                score += CARD_VALUES.get(card);
                if ("A".equals(card)) {
                    aceCount++;
                }
            }

            while (score > 21 && aceCount > 0) {
                score -= 10;
                aceCount--;
            }

            return score;
        }

        private void checkGameOver() {
            synchronized (players) {
                boolean allStopped = players.stream().allMatch(player -> player.hasStopped);
                if (allStopped) {
                    PlayerHandler player1 = players.get(0);
                    PlayerHandler player2 = players.get(1);

                    int score1 = player1.calculateScore(player1.playerCards);
                    int score2 = player2.calculateScore(player2.playerCards);

                    String winnerMessagePlayer1;
                    String winnerMessagePlayer2;

                    if (score1 > score2 && score1 <= 21 || score2 > 21) {
                        winnerMessagePlayer1 = "Игра окончена! Вы победили с счетом " + score1;
                        winnerMessagePlayer2 = "Игра окончена! Вы проиграли. Победитель: Игрок 1";
                    } else if (score2 > score1 && score2 <= 21 || score1 > 21) {
                        winnerMessagePlayer1 = "Игра окончена! Вы проиграли. Победитель: Игрок 2";
                        winnerMessagePlayer2 = "Игра окончена! Вы победили с счетом " + score2;
                    } else {
                        winnerMessagePlayer1 = "Игра окончена! Ничья!";
                        winnerMessagePlayer2 = "Игра окончена! Ничья!";
                    }

                    player1.sendMessage(winnerMessagePlayer1);
                    player2.sendMessage(winnerMessagePlayer2);

                    endGame("Игра завершена.");
                }
            }
        }

        private int getOtherPlayerId() {
            return playerId == 1 ? 2 : 1;
        }
    }
}