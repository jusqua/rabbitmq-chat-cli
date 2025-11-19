package br.ufs.dcomp.ChatRabbitMQ;

import br.ufs.dcomp.Message;
import com.google.protobuf.ByteString;
import com.rabbitmq.client.*;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicReference;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.TerminalBuilder;

public class Chat {

  public static void main(String[] argv) throws Exception {
    var terminal = TerminalBuilder.terminal();
    var reader = LineReaderBuilder.builder().terminal(terminal).build();

    var currentUser = new AtomicReference<String>("");
    var currentDestinatary = "";
    var currentGroup = "";

    while (currentUser.get().isBlank()) {
      try {
        currentUser.set(reader.readLine("User: "));
      } catch (final Exception e) {
        System.out.println("Exited");
        return;
      }
    }

    var factory = new ConnectionFactory();
    factory.setHost("localhost");

    var connection = factory.newConnection();
    var channel = connection.createChannel();

    channel.queueDeclare(currentUser.get(), false, false, false, null);
    channel.basicConsume(
      currentUser.get(),
      true,
      new DefaultConsumer(channel) {
        public void handleDelivery(
          String consumerTag,
          Envelope envelope,
          AMQP.BasicProperties properties,
          byte[] body
        ) {
          try {
            recieveMessage(reader, Message.parseFrom(body), currentUser.get());
          } catch (final Exception e) {
            e.printStackTrace();
          }
        }
      }
    );

    String prompt;
    while (true) {
      try {
        prompt = reader.readLine(
          String.format(
            "%s%s<< ",
            currentDestinatary.isBlank() ? "" : "@" + currentDestinatary,
            currentGroup.isBlank() ? "" : "#" + currentGroup
          )
        );
      } catch (final Exception e) {
        System.out.println("Logged out");
        break;
      }

      if (prompt.isBlank()) continue;

      if (prompt.charAt(0) == '!') {
        var args = prompt.substring(1).trim().split(" ");
        var command = args[0];
        if (command.isBlank()) {
          System.out.println("No command provided");
        } else if (command.equals("help")) {
          System.out.println("--- Select a destinatary ---");
          System.out.println("@<destinatary-user-name>");
          System.out.println("--- Select a group ---");
          System.out.println("#<group-name>");
          System.out.println();
          System.out.println("--- Commands ---");
          System.out.println("!create-group <group-name>\tCreate a chat group");
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
        } else if (command.equals("create-group")) {
          if (args.length != 2) {
            System.out.println("Usage: !create-group <group-name>");
            continue;
          }
          var groupName = args[1];
          channel.exchangeDeclare(groupName, "fanout");
          channel.queueBind(currentUser.get(), groupName, "");
        } else if (command.equals("delete-group")) {
          if (args.length != 2) {
            System.out.println("Usage: !delete-group <group-name>");
            continue;
          }

          var groupName = args[1];
          if (!isGroupExists(connection, groupName)) {
            System.out.println("Group does not exist");
            continue;
          }

          channel.exchangeDelete(groupName);
        } else if (command.equals("add-user")) {
          if (args.length != 3) {
            System.out.println("Usage: !add-user <user-name> <group-name>");
            continue;
          }

          var userName = args[1];
          if (!isUserExists(connection, userName)) {
            System.out.println("User does not exist");
            continue;
          }

          var groupName = args[2];
          if (!isGroupExists(connection, groupName)) {
            System.out.println("Group does not exist");
            continue;
          }

          channel.queueBind(userName, groupName, "");
        } else if (command.equals("remove-user")) {
          if (args.length != 3) {
            System.out.println("Usage: !remove-user <user-name> <group-name>");
            continue;
          }

          var userName = args[1];
          if (!isUserExists(connection, userName)) {
            System.out.println("User does not exist");
            continue;
          }

          var groupName = args[2];
          if (!isGroupExists(connection, groupName)) {
            System.out.println("Group does not exist");
            continue;
          }

          channel.queueUnbind(userName, groupName, "");
        } else if (command.equals("leave-group")) {
          if (args.length != 2) {
            System.out.println("Usage: !leave-group <group-name>");
            continue;
          }

          var groupName = args[1];
          if (!isGroupExists(connection, groupName)) {
            System.out.println("Group does not exist");
            continue;
          }

          channel.queueUnbind(currentUser.get(), groupName, "");
        } else if (command.equals("exit")) {
          System.out.println("Logged out");
          break;
        } else {
          System.out.println(
            String.format(
              "\"%s\" is not in list of available commands",
              command
            )
          );
        }
        continue;
      }

      if (prompt.charAt(0) == '@') {
        var destinatary = prompt.substring(1).trim();
        if (destinatary.isBlank()) {
          System.out.println("No destinatary provided");
        } else if (destinatary.equals(currentUser.get())) {
          System.out.println("Destinatary cannot be yourself");
        } else if (!isUserExists(connection, destinatary)) {
          System.out.println("Destinatary does not exist");
        } else {
          currentDestinatary = destinatary;
          currentGroup = "";
        }
        continue;
      }

      if (prompt.charAt(0) == '#') {
        var group = prompt.substring(1).trim();
        if (group.isBlank()) {
          System.out.println("No group provided");
        } else if (!isGroupExists(connection, group)) {
          System.out.println("Group does not exist");
        } else {
          currentDestinatary = "";
          currentGroup = group;
        }
        continue;
      }

      if (currentDestinatary.isBlank() && currentGroup.isBlank()) {
        System.out.println("Use @<user-name> to set a destinatary");
        System.out.println("Use #<group-name> to set a group");
        continue;
      }

      sendMessage(
        channel,
        currentDestinatary,
        currentGroup,
        currentUser.get(),
        prompt
      );
    }

    connection.close();
  }

  public static boolean isGroupExists(Connection connection, String groupName) {
    try {
      var tmp = connection.createChannel();
      tmp.exchangeDeclarePassive(groupName);
      tmp.close();
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public static boolean isUserExists(Connection connection, String userName) {
    try {
      var tmp = connection.createChannel();
      tmp.queueDeclarePassive(userName);
      tmp.close();
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public static void recieveMessage(
    LineReader reader,
    Message message,
    String currentUser
  ) {
    if (message.getSender().equals(currentUser)) {
      return;
    }

    reader.printAbove(
      String.format(
        "(%s) %s%s says: %s",
        message.getDatetime(),
        "@" + message.getSender(),
        message.hasGroup() ? "#" + message.getGroup() : "",
        message.getBody().toStringUtf8()
      )
    );
  }

  public static void sendMessage(
    Channel channel,
    String destinatary,
    String groupName,
    String sender,
    String text
  ) {
    try {
      var builder = Message.newBuilder()
        .setSender(sender)
        .setBody(ByteString.copyFromUtf8(text))
        .setDatetime(
          LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
          )
        );

      if (!groupName.isBlank()) {
        builder = builder.setGroup(groupName);
      }

      var payload = builder.build().toByteArray();

      channel.basicPublish(groupName, destinatary, null, payload);
    } catch (final IOException exception) {
      System.err.println(
        "An error occurred while sending the message: " + exception.getMessage()
      );
    }
  }
}
