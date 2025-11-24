package br.ufs.dcomp.ChatRabbitMQ;

import br.ufs.dcomp.Message;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import io.github.cdimascio.dotenv.Dotenv;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.TerminalBuilder;

public class Main {

  public static void main(String[] argv) throws IOException, TimeoutException {
    var env = Dotenv.configure().ignoreIfMissing().load();
    var host = env.get("HOST", "localhost");

    var reader = LineReaderBuilder.builder()
      .terminal(TerminalBuilder.terminal())
      .build();

    var username = new AtomicReference<String>("");
    while (username.get().isBlank()) {
      try {
        username.set(reader.readLine("User: "));
      } catch (final Exception e) {
        System.out.println("Exited");
        return;
      }
    }

    var chat = new Chat(host, username.get(), channel ->
      new DefaultConsumer(channel) {
        public void handleDelivery(
          String consumerTag,
          Envelope envelope,
          AMQP.BasicProperties properties,
          byte[] body
        ) {
          try {
            var message = Message.parseFrom(body);

            if (message.getSender().equals(username.get())) return;

            reader.printAbove(
              String.format(
                "(%s) %s%s says: %s",
                message.getDatetime(),
                "@" + message.getSender(),
                message.hasGroup() ? "#" + message.getGroup() : "",
                message.getBody().toStringUtf8()
              )
            );
          } catch (final Exception e) {
            e.printStackTrace();
          }
        }
      }
    );

    while (chat.getIsActive()) {
      String prompt;
      try {
        prompt = reader.readLine(
          String.format(
            "%s%s<< ",
            chat.getCurrentDestinatary().isBlank()
              ? ""
              : "@" + chat.getCurrentDestinatary(),
            chat.getCurrentGroup().isBlank() ? "" : "#" + chat.getCurrentGroup()
          )
        );
      } catch (final EndOfFileException | UserInterruptException e) {
        chat.close();
        System.out.println("Logged out");
        break;
      }

      if (prompt.isBlank()) continue;

      if (prompt.charAt(0) == '!') {
        var args = prompt.substring(1).trim().split(" ");
        if (args.length == 0) System.err.println("Invalid command");

        try {
          switch (args[0]) {
            case "create-group":
              chat.createGroup(args[1]);
              break;
            case "delete-group":
              chat.deleteGroup(args[1]);
              break;
            case "add-user":
              chat.addUserToGroup(args[1], args[2]);
              break;
            case "remove-user":
              chat.removeUserToGroup(args[1], args[2]);
              break;
            case "leave-group":
              chat.leaveGroup(args[1]);
              break;
            case "exit":
              chat.close();
              System.out.println("Logged out");
              break;
            case "help":
              System.out.println("--- Select a destinatary ---");
              System.out.println("@<destinatary-user-name>");
              System.out.println("--- Select a group ---");
              System.out.println("#<group-name>");
              System.out.println();
              System.out.println("--- Commands ---");
              System.out.println(
                "!create-group <group-name>\tCreate a chat group"
              );
              System.out.println(
                "!delete-group <group-name>\tDelete an existing chat group"
              );
              System.out.println(
                "!add-user <user-name> <group-name>\tAdd user to existing group"
              );
              System.out.println(
                "!remove-user <user-name> <group-name>\tRemove user from existing group"
              );
              System.out.println(
                "!leave-group <group-name>\tLeave an existing group"
              );
              System.out.println("!help\tDisplay help");
              System.out.println("!exit\tEnd program");
              break;
            default:
              System.out.println(
                String.format(
                  "\"%s\" is not in list of available commands",
                  prompt
                )
              );
              break;
          }
        } catch (final ChatException e) {
          System.err.println(e.getMessage());
        }
        continue;
      }

      if (prompt.charAt(0) == '@') {
        var args = prompt.substring(1).trim().split(" ");
        if (args.length > 1) System.err.println("Invalid command");
        try {
          chat.setDestinatary(args[0]);
        } catch (final ChatException e) {
          System.err.println(e.getMessage());
        }
        continue;
      }

      if (prompt.charAt(0) == '#') {
        var args = prompt.substring(1).trim().split(" ");
        if (args.length > 1) System.err.println("Invalid command");
        try {
          chat.setGroup(args[0]);
        } catch (final ChatException e) {
          System.err.println(e.getMessage());
        }
        continue;
      }

      if (
        chat.getCurrentGroup().isBlank() &&
        chat.getCurrentDestinatary().isBlank()
      ) {
        System.out.println("Use @<user-name> to set a destinatary");
        System.out.println("Use #<group-name> to set a group");
        continue;
      }

      try {
        chat.sendMessage(prompt);
      } catch (final ChatException e) {
        System.err.println(e.getMessage());
      }
    }
  }
}
