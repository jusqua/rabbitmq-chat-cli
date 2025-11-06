package br.ufs.dcomp.ChatRabbitMQ;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Message implements Serializable {
  private String content;
  private String sender;
  private LocalDateTime time;

  public Message(String content, String sender) {
    this.content = content;
    this.sender = sender;
    this.time = LocalDateTime.now();
  }

  public static byte[] toBytes(Message message) throws IOException {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
      try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
        out.writeObject(message);
        out.flush();
      }
      return bos.toByteArray();
    }
  }

  public static Message fromBytes(byte[] bytes) throws IOException, ClassNotFoundException {
    try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        ObjectInputStream in = new ObjectInputStream(bis)) {
      return (Message) in.readObject();
    }
  }

  @Override
  public String toString() {
    return String.format(
        "(%s) @%s says: %s",
        time.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")),
        sender,
        content);
  }
}
