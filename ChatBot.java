import java.util.Scanner;

public class ChatBot {
    private Scanner scanner;
    private boolean running;

    public ChatBot() {
        this.scanner = new Scanner(System.in);
        this.running = false;
    }

    public void start() {
        running = true;
        System.out.println("ChatBot is ready! Type 'quit' to exit.");
        
        while (running) {
            System.out.print("You: ");
            String input = scanner.nextLine();
            
            if (input.equalsIgnoreCase("quit")) {
                stop();
            } else {
                String response = generateResponse(input);
                System.out.println("Bot: " + response);
            }
        }
    }

    public void stop() {
        running = false;
        System.out.println("ChatBot shutting down. Goodbye!");
        scanner.close();
    }

    private String generateResponse(String input) {
        // Simple response logic - can be expanded
        input = input.toLowerCase();
        
        if (input.contains("hello") || input.contains("hi")) {
            return "Hello! How can I help you today?";
        } else if (input.contains("how are you")) {
            return "I'm doing great, thanks for asking!";
        } else if (input.contains("name")) {
            return "I'm ChatBot, your friendly assistant!";
        } else if (input.contains("help")) {
            return "I'm here to chat with you. Just type a message!";
        } else {
            return "Interesting! Tell me more about that.";
        }
    }
}
