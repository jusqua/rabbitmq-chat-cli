package br.ufs.dcomp.ChatRabbitMQ;

import br.ufs.dcomp.Message;
import br.ufs.dcomp.Message.Builder;
import com.google.protobuf.ByteString;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;

public class Chat implements AutoCloseable {

  private static final String fileNamespace = "chat.file";
  private static final String textNamespace = "chat.text";

  private final Connection connection;
  private final BiFunction<Channel, String, Consumer> factory;

  private Channel channel;
  private String currentUser;
  private String currentDestinatary;
  private String currentGroup;

  Chat(
    String host,
    String vhost,
    String user,
    String password,
    BiFunction<Channel, String, Consumer> factory
  ) throws IOException, TimeoutException, URISyntaxException {
    var connectionFactory = new ConnectionFactory();
    connectionFactory.setHost(host);
    connectionFactory.setUsername(user);
    connectionFactory.setPassword(password);
    connectionFactory.setVirtualHost(vhost);

    this.connection = connectionFactory.newConnection();
    this.channel = this.connection.createChannel();

    this.currentUser = "";
    this.currentDestinatary = "";
    this.currentGroup = "";

    this.factory = factory;
  }

  private static String getFileQueue(final String userName) {
    return Chat.fileNamespace + "." + userName;
  }

  private static String getTextQueue(final String userName) {
    return Chat.textNamespace + "." + userName;
  }

  @Override
  public void close() throws IOException, TimeoutException {
    this.channel.close();
    this.connection.close();
  }

  private boolean isGroupExists(String groupName) {
    try {
      var tmp = this.connection.createChannel();
      tmp.exchangeDeclarePassive(groupName);
      tmp.close();
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private boolean isUserExists(String userName) {
    try {
      var tmp = this.connection.createChannel();
      tmp.queueDeclarePassive(getTextQueue(userName));
      tmp.close();
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private Builder createDefaultMessageBuilder() {
    return Message.newBuilder().setDatetime(
      LocalDateTime.now().format(
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
      )
    );
  }

  private void sendSystem(String text) {
    var payload = createDefaultMessageBuilder()
      .setBody(ByteString.copyFromUtf8(text))
      .build()
      .toByteArray();

    try {
      this.channel.basicPublish(
        "",
        getTextQueue(this.currentUser),
        null,
        payload
      );
    } catch (final Exception e) {
      return;
    }
  }

  public boolean isOpen() {
    return this.connection.isOpen();
  }

  public String getCurrentUser() {
    return this.currentUser;
  }

  public String getCurrentDestinatary() {
    return this.currentDestinatary;
  }

  public String getCurrentGroup() {
    return this.currentGroup;
  }

  public void logIn(String userName) throws ChatException {
    if (!this.currentUser.isEmpty()) {
      throw new ChatException("Already logged in");
    }

    try {
      this.channel.queueDeclare(
        getFileQueue(userName),
        false,
        false,
        false,
        null
      );
      this.channel.queueDeclare(
        getTextQueue(userName),
        false,
        false,
        false,
        null
      );

      this.channel.basicConsume(
        getFileQueue(userName),
        true,
        this.factory.apply(channel, userName)
      );
      this.channel.basicConsume(
        getTextQueue(userName),
        true,
        this.factory.apply(channel, userName)
      );

      this.currentUser = userName;
      this.currentDestinatary = "";
      this.currentGroup = "";
    } catch (final Exception e) {
      throw new ChatException("Could not log in");
    }
  }

  public void logOut() throws ChatException {
    if (this.currentUser.isEmpty()) {
      throw new ChatException("Not logged in");
    }

    try {
      this.currentUser = "";
      this.currentDestinatary = "";
      this.currentGroup = "";
      this.channel.close();
      this.channel = this.connection.createChannel();
    } catch (final Exception e) {
      throw new ChatException("Could not log out");
    }
  }

  public void deleteGroup(String groupName) throws ChatException {
    if (!isGroupExists(groupName)) {
      throw new ChatException("Given group does not exist");
    }

    try {
      this.channel.exchangeDelete(groupName);
    } catch (final Exception e) {
      throw new ChatException("Could not delete group");
    }
  }

  public void addUserToGroup(String userName, String groupName)
    throws ChatException {
    if (!isUserExists(userName)) {
      throw new ChatException("Given user does not exist");
    } else if (!isGroupExists(groupName)) {
      throw new ChatException("Given group does not exist");
    }

    try {
      this.channel.queueBind(
        getFileQueue(userName),
        groupName,
        Chat.fileNamespace
      );
      this.channel.queueBind(
        getTextQueue(userName),
        groupName,
        Chat.textNamespace
      );
    } catch (final Exception e) {
      throw new ChatException("Could not add user to group");
    }
  }

  public void createGroup(String groupName) throws ChatException {
    if (isGroupExists(groupName)) {
      throw new ChatException("Given group already exist");
    }

    try {
      this.channel.exchangeDeclare(groupName, "direct");
      addUserToGroup(this.currentUser, groupName);
    } catch (final Exception e) {
      throw new ChatException("Could not create group");
    }
  }

  public void removeUserFromGroup(String userName, String groupName)
    throws ChatException {
    if (!isUserExists(userName)) {
      throw new ChatException("Given user does not exist");
    } else if (!isGroupExists(groupName)) {
      throw new ChatException("Given group does not exist");
    }

    try {
      this.channel.queueUnbind(
        getFileQueue(userName),
        groupName,
        Chat.fileNamespace
      );
      this.channel.queueUnbind(
        getTextQueue(userName),
        groupName,
        Chat.textNamespace
      );
    } catch (final Exception e) {
      if (userName.equals(this.currentUser)) {
        throw new ChatException("Could not leave group");
      } else {
        throw new ChatException("Could not remove user from group");
      }
    }
  }

  public void leaveGroup(String groupName) throws ChatException {
    removeUserFromGroup(groupName, this.currentUser);
  }

  public void setDestinatary(String userName) throws ChatException {
    if (userName.isBlank()) {
      throw new ChatException("No valid destinatary given");
    } else if (userName.equals(this.currentUser)) {
      throw new ChatException("Given destinatary cannot be yourself");
    } else if (!isUserExists(userName)) {
      throw new ChatException("Given destinatary does not exist");
    }

    this.currentDestinatary = userName;
    this.currentGroup = "";
  }

  public void setGroup(String groupName) throws ChatException {
    if (groupName.isBlank()) {
      throw new ChatException("No valid group given");
    } else if (!isGroupExists(groupName)) {
      throw new ChatException("Given group does not exist");
    }

    this.currentGroup = groupName;
    this.currentDestinatary = "";
  }

  public void sendText(String text) throws ChatException {
    var builder = Message.newBuilder()
      .setSender(this.currentUser)
      .setBody(ByteString.copyFromUtf8(text))
      .setDatetime(
        LocalDateTime.now().format(
          DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
        )
      );

    if (!this.currentGroup.isBlank()) {
      builder = builder.setGroup(this.currentGroup);
    }

    var payload = builder.build().toByteArray();

    try {
      this.channel.basicPublish(
        this.currentGroup,
        getTextQueue(this.currentDestinatary),
        null,
        payload
      );
    } catch (final Exception e) {
      throw new ChatException("Could not send message");
    }
  }

  public void sendFile(String filepath) throws ChatException {
    Path path;
    String type;
    String filename;

    filepath = filepath.replaceFirst("^~", System.getProperty("user.home"));

    try {
      path = FileSystems.getDefault()
        .getPath(filepath)
        .normalize()
        .toAbsolutePath();
    } catch (final Exception e) {
      throw new ChatException("Could not find the file");
    }

    if (!Files.isRegularFile(path)) {
      throw new ChatException("Path is not a regular file");
    }

    try {
      type = Files.probeContentType(path);
    } catch (final Exception e) {
      throw new ChatException("Could not probe content type");
    }

    filename = path.getFileName().toString();

    new Thread(() -> {
      byte[] content;
      try {
        content = Files.readAllBytes(path);
      } catch (final Exception e) {
        sendSystem("Could not read file " + filename);
        return;
      }

      var builder = createDefaultMessageBuilder()
        .setSender(this.currentUser)
        .setBody(ByteString.copyFrom(content))
        .setType(type)
        .setFilename(filename);

      if (!this.currentGroup.isEmpty()) {
        builder = builder.setGroup(this.currentGroup);
      }

      var payload = builder.build().toByteArray();

      String destination = String.format(
        this.currentDestinatary.isBlank()
          ? "group " + this.currentGroup
          : "user " + this.currentDestinatary
      );
      try {
        this.channel.basicPublish(
          this.currentGroup,
          getFileQueue(this.currentDestinatary),
          null,
          payload
        );
        sendSystem("File " + filename + " was sent to " + destination);
      } catch (final Exception e) {
        sendSystem("Could not send file " + filename + " to " + destination);
        return;
      }
    })
      .start();
  }
}
