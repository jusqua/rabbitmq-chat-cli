package br.ufs.dcomp.ChatRabbitMQ;

import br.ufs.dcomp.Message;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import io.github.cdimascio.dotenv.Dotenv;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.TerminalBuilder;

public class Main {

  public static void main(String[] argv)
    throws IOException, TimeoutException, URISyntaxException {
    var env = Dotenv.configure().ignoreIfMissing().load();

    final var RABBITMQ_HOST = env.get("RABBITMQ_HOST", "localhost");
    final var RABBITMQ_VHOST = env.get("RABBITMQ_VHOST", "/");
    final var RABBITMQ_PORT = env.get("RABBITMQ_PORT", "15672");
    final var RABBITMQ_USER = env.get("RABBITMQ_USER", "guest");
    final var RABBITMQ_PASSWORD = env.get("RABBITMQ_PASSWORD", "guest");
    final var CHAT_DOWNLOAD_FOLDER = env.get(
      "CHAT_DOWNLOAD_FOLDER",
      Paths.get(System.getProperty("user.home"), "Downloads").toString()
    );

    final var READER = LineReaderBuilder.builder()
      .terminal(TerminalBuilder.terminal())
      .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
      .build();

    final var chat = new Chat(
      RABBITMQ_HOST,
      RABBITMQ_VHOST,
      RABBITMQ_PORT,
      RABBITMQ_USER,
      RABBITMQ_PASSWORD,
      (channel, username) ->
        new DefaultConsumer(channel) {
          public void handleDelivery(
            String consumerTag,
            Envelope envelope,
            AMQP.BasicProperties properties,
            byte[] body
          ) {
            try {
              var message = Message.parseFrom(body);

              if (message.getSender().equals(username)) return;

              if (!message.hasSender()) {
                READER.printAbove(
                  String.format(
                    "(%s) System reports: %s",
                    message.getDatetime(),
                    message
                      .getBody()
                      .toStringUtf8()
                      .replaceFirst("user=", ChatSymbol.USER_SYMBOL.toString())
                      .replaceFirst(
                        "group=",
                        ChatSymbol.GROUP_SYMBOL.toString()
                      )
                  )
                );
                return;
              }

              if (message.hasType() && message.hasFilename()) {
                Files.write(
                  Path.of(CHAT_DOWNLOAD_FOLDER, message.getFilename()),
                  message.getBody().toByteArray()
                );
                READER.printAbove(
                  String.format(
                    "(%s) File %s received from %s%s",
                    message.getDatetime(),
                    message.getFilename(),
                    ChatSymbol.USER_SYMBOL + message.getSender(),
                    message.hasGroup()
                      ? ChatSymbol.GROUP_SYMBOL + message.getGroup()
                      : ""
                  )
                );
                return;
              }

              READER.printAbove(
                String.format(
                  "(%s) %s%s says: %s",
                  message.getDatetime(),
                  ChatSymbol.USER_SYMBOL + message.getSender(),
                  message.hasGroup()
                    ? ChatSymbol.GROUP_SYMBOL + message.getGroup()
                    : "",
                  message.getBody().toStringUtf8()
                )
              );
            } catch (final Exception e) {
              e.printStackTrace();
            }
          }
        }
    );

    var commandHandler = new ChatCommandHandler(chat);

    while (chat.isOpen()) {
      String response;

      try {
        String prompt = "";
        if (chat.getUserName().isBlank()) {
          prompt = ChatSymbol.LOGIN_TEXT;
        } else {
          prompt = chat.getUserName();
          prompt += chat
            .getDestinatary()
            .replaceFirst("user=", ChatSymbol.USER_SYMBOL.toString())
            .replaceFirst("group=", ChatSymbol.GROUP_SYMBOL.toString());
          prompt += ChatSymbol.PROMPT_TEXT;
        }

        response = READER.readLine(prompt);
      } catch (final EndOfFileException | UserInterruptException e) {
        chat.close();
        break;
      }

      if (response.isBlank()) continue;

      if (chat.getUserName().isEmpty()) {
        try {
          var username = response.trim();
          chat.logIn(username);
          System.out.println("Logged in as " + username);
          continue;
        } catch (final ChatException e) {
          System.err.println(e.getMessage());
          continue;
        }
      }

      var symbol = response.trim().charAt(0);
      if (!Arrays.asList(ChatSymbol.COMMAND_SYMBOLS).contains(symbol)) {
        symbol = ChatSymbol.TEXT_SYMBOL;
        response = ChatSymbol.TEXT_SYMBOL + response;
      }
      var args = response.trim().substring(1).split(" ");
      commandHandler.apply(symbol, args);
    }

    System.out.println("Exited");
  }
}
