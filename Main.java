public class Main {
    public static void main(String[] args) {
        Thread serverThread = new Thread(() -> {
            try {
                Server21.main(new String[]{});
            } catch (Exception e) {
                System.err.println("Ошибка при запуске сервера: " + e.getMessage());
            }
        });

        Thread clientThread = new Thread(() -> {
            try {
                Client21.main(new String[]{});
            } catch (Exception e) {
                System.err.println("Ошибка при запуске клиента: " + e.getMessage());
            }
        });

        serverThread.start();

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            System.err.println("Ошибка синхронизации потоков: " + e.getMessage());
        }

        clientThread.start();
    }
}

