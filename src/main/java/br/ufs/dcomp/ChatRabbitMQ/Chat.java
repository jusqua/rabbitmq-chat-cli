package br.ufs.dcomp.ChatRabbitMQ;

import br.ufs.dcomp.Message;
import com.google.protobuf.ByteString;
import com.rabbitmq.client.*;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.TerminalBuilder;

public class Chat {

  public static void main(String[] argv) throws Exception {
    var terminal = TerminalBuilder.terminal();
    var reader = LineReaderBuilder.builder().terminal(terminal).build();

    var currentUser = "";
    var currentDestinatary = "";

    while (currentUser.isBlank()) {
      try {
        currentUser = reader.readLine("User: ");
      } catch (final Exception e) {
        System.out.println("Exited");
        return;
      }
    }

    var factory = new ConnectionFactory();
    factory.setHost("localhost");

    var connection = factory.newConnection();
    var channel = connection.createChannel();

    channel.queueDeclare(currentUser, false, false, false, null);
    channel.basicConsume(
      currentUser,
      true,
      new DefaultConsumer(channel) {
        public void handleDelivery(
          String consumerTag,
          Envelope envelope,
          AMQP.BasicProperties properties,
          byte[] body
        ) {
          try {
            recieveMessage(reader, Message.parseFrom(body));
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
          (currentDestinatary.isBlank() ? "" : ("@" + currentDestinatary)) +
            "<< "
        );
      } catch (final Exception e) {
        System.out.println("Logged out");
        break;
      }

      if (prompt.isBlank()) continue;

      if (prompt.charAt(0) == '!') {
        var command = prompt.substring(1).trim();
        if (command.isBlank()) {
          System.out.println("No command provided");
        } else if (command.equals("help")) {
          System.out.println("--- Set destinatary for current user ---");
          System.out.println("@<destinatary>");
          System.out.println();
          System.out.println("--- Commands ---");
          System.out.println("!help\tDisplay help");
          System.out.println("!exit\tEnd program");
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
        } else if (destinatary.equals(currentUser)) {
          System.out.println("Destinatary cannot be yourself");
        } else {
          try {
            var tmp = connection.createChannel();
            tmp.queueDeclarePassive(destinatary);
            currentDestinatary = destinatary;
            tmp.close();
          } catch (Exception e) {
            System.out.println("Destinatary does not exist");
          }
        }
        continue;
      }

      if (currentDestinatary.isBlank()) {
        System.out.println("Use @<destinatary> to set a destinatary");
        continue;
      }

      sendMessage(channel, currentDestinatary, currentUser, prompt);
    }

    connection.close();
  }

  public static void recieveMessage(LineReader reader, Message message) {
    reader.printAbove(
      String.format(
        "(%s) @%s says: %s",
        message.getDatetime(),
        message.getSender(),
        message.getBody().toStringUtf8()
      )
    );
  }

  public static void sendMessage(
    Channel channel,
    String destinatary,
    String sender,
    String text
  ) {
    try {
      var payload = Message.newBuilder()
        .setSender(sender)
        .setBody(ByteString.copyFromUtf8(text))
        .setDatetime(
          LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
          )
        )
        .build()
        .toByteArray();

      channel.basicPublish("", destinatary, null, payload);
    } catch (final IOException exception) {
      System.err.println(
        "An error occurred while sending the message: " + exception.getMessage()
      );
    }
  }
}
