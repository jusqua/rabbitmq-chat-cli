package br.ufs.dcomp.ChatRabbitMQ;

import br.ufs.dcomp.Message;
import br.ufs.dcomp.Message.Builder;
import com.google.gson.JsonParser;
import com.google.protobuf.ByteString;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;

public class Chat implements AutoCloseable {

  private static final String groupNamespace = "chat.group";
  private static final String fileNamespace = "chat.file";
  private static final String textNamespace = "chat.text";

  private final HttpClient client;
  private final Connection connection;
  private final BiFunction<Channel, String, Consumer> factory;

  private final String auth;
  private final URI host;
  private final String vhost;

  private Channel channel;
  private String userName;
  private String routingKey;
  private String exchange;

  Chat(
    String host,
    String vhost,
    String port,
    String user,
    String password,
    BiFunction<Channel, String, Consumer> factory
  ) throws IOException, TimeoutException, URISyntaxException {
    var credentials = user + ":" + password;
    this.auth = Base64.getEncoder().encodeToString(credentials.getBytes());
    this.host = new URI("http://" + host + ":" + port);
    this.vhost = URLEncoder.encode(vhost, StandardCharsets.UTF_8);

    var connectionFactory = new ConnectionFactory();
    connectionFactory.setHost(host);
    connectionFactory.setUsername(user);
    connectionFactory.setPassword(password);
    connectionFactory.setVirtualHost(vhost);

    this.client = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5))
      .build();

    this.connection = connectionFactory.newConnection();
    this.channel = this.connection.createChannel();

    this.userName = "";
    this.routingKey = "";
    this.exchange = "";

    this.factory = factory;
  }

  private static String getGroupExchange(final String groupName) {
    if (groupName.isEmpty()) return "";
    return Chat.groupNamespace + "." + groupName;
  }

  private static String getFileQueue(final String userName) {
    if (userName.isEmpty()) return Chat.fileNamespace;
    return Chat.fileNamespace + "." + userName;
  }

  private static String getTextQueue(final String userName) {
    if (userName.isEmpty()) return Chat.textNamespace;
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
      tmp.exchangeDeclarePassive(getGroupExchange(groupName));
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
      this.channel.basicPublish("", getTextQueue(this.userName), null, payload);
    } catch (final Exception e) {
      return;
    }
  }

  public boolean isOpen() {
    return this.connection.isOpen();
  }

  public String getUserName() {
    return this.userName;
  }

  public String getRoutingKey() {
    return this.routingKey;
  }

  public String getExchange() {
    return this.exchange;
  }

  public void logIn(String userName) throws ChatException {
    if (!this.userName.isEmpty()) {
      throw new ChatException("Already logged in");
    }

    var args = new HashMap<String, Object>();
    args.put("x-queue-type", "quorum");

    try {
      this.channel.queueDeclare(
        getFileQueue(userName),
        true,
        false,
        false,
        args
      );
      this.channel.queueDeclare(
        getTextQueue(userName),
        true,
        false,
        false,
        args
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

      this.userName = userName;
      this.routingKey = "";
      this.exchange = "";
    } catch (final Exception e) {
      e.printStackTrace();
      throw new ChatException("Could not log in");
    }
  }

  public void logOut() throws ChatException {
    if (this.userName.isEmpty()) {
      throw new ChatException("Not logged in");
    }

    try {
      this.userName = "";
      this.routingKey = "";
      this.exchange = "";
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
      this.channel.exchangeDelete(getGroupExchange(groupName));
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
        getGroupExchange(groupName),
        Chat.fileNamespace
      );
      this.channel.queueBind(
        getTextQueue(userName),
        getGroupExchange(groupName),
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
      this.channel.exchangeDeclare(getGroupExchange(groupName), "direct");
      addUserToGroup(this.userName, groupName);
      setDestinatary(groupName, true);
    } catch (final IOException e) {
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
        getGroupExchange(groupName),
        Chat.fileNamespace
      );
      this.channel.queueUnbind(
        getTextQueue(userName),
        getGroupExchange(groupName),
        Chat.textNamespace
      );
    } catch (final Exception e) {
      if (userName.equals(this.userName)) {
        throw new ChatException("Could not leave group");
      } else {
        throw new ChatException("Could not remove user from group");
      }
    }
  }

  public void leaveGroup(String groupName) throws ChatException {
    removeUserFromGroup(groupName, this.userName);
  }

  public ArrayList<String> listUsers(String groupName) throws ChatException {
    if (!isGroupExists(groupName)) {
      throw new ChatException("Given group does not exist");
    }
    var list = new ArrayList<String>();

    groupName = URLEncoder.encode(
      getGroupExchange(groupName),
      StandardCharsets.UTF_8
    );
    var uri = this.host.resolve(
      "/api/exchanges/" + this.vhost + "/" + groupName + "/bindings/source"
    );
    var request = HttpRequest.newBuilder()
      .uri(uri)
      .header("Authorization", "Basic " + auth)
      .GET()
      .build();
    try {
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new ChatException("Fail to retrieve data from server");
      }
      for (var e : JsonParser.parseString(response.body()).getAsJsonArray()) {
        String destination = e
          .getAsJsonObject()
          .get("destination")
          .getAsString();
        if (destination.startsWith(Chat.fileNamespace)) {
          list.add(destination.substring(Chat.fileNamespace.length() + 1));
        }
      }
    } catch (final IOException e) {
      throw new ChatException("Could not retrieve data");
    } catch (final InterruptedException e) {
      throw new ChatException("Time limit to retrieve data exceeded");
    }
    return list;
  }

  public ArrayList<String> listGroups() throws ChatException {
    var list = new ArrayList<String>();
    var uri = this.host.resolve("/api/exchanges/" + this.vhost);
    var request = HttpRequest.newBuilder()
      .uri(uri)
      .header("Authorization", "Basic " + auth)
      .GET()
      .build();
    try {
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new ChatException("Fail to retrieve data from server");
      }
      for (var e : JsonParser.parseString(response.body()).getAsJsonArray()) {
        String name = e.getAsJsonObject().get("name").getAsString();
        if (name.startsWith(Chat.groupNamespace)) {
          var groupName = name.substring(Chat.groupNamespace.length() + 1);
          var userList = listUsers(groupName);
          if (userList.contains(this.userName)) {
            list.add(groupName);
          }
        }
      }
    } catch (final IOException e) {
      throw new ChatException("Could not retrieve data");
    } catch (final InterruptedException e) {
      throw new ChatException("Time limit to retrieve data exceeded");
    }
    return list;
  }

  public String getDestinatary() {
    if (!this.routingKey.isEmpty()) {
      return "user=" + this.routingKey;
    } else if (!this.exchange.isEmpty()) {
      return "group=" + this.exchange;
    }
    return "";
  }

  public void setDestinatary(String target, boolean isGroup)
    throws ChatException {
    if (target.isBlank()) {
      throw new ChatException("No blank destinatary given");
    } else if (!isGroup && target.equals(this.userName)) {
      throw new ChatException("Given destinatary cannot be yourself");
    } else if (!isGroup && !isUserExists(target)) {
      throw new ChatException("Given user does not exist");
    } else if (isGroup && !isGroupExists(target)) {
      throw new ChatException("Given group does not exist");
    }

    if (isGroup) {
      this.routingKey = "";
      this.exchange = target;
    } else {
      this.routingKey = target;
      this.exchange = "";
    }
  }

  public boolean hasDestinatary() {
    return !this.exchange.isBlank() || !this.routingKey.isBlank();
  }

  public void sendText(String text) throws ChatException {
    var builder = Message.newBuilder()
      .setSender(this.userName)
      .setBody(ByteString.copyFromUtf8(text))
      .setDatetime(
        LocalDateTime.now().format(
          DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
        )
      );

    if (!this.exchange.isBlank()) {
      builder = builder.setGroup(this.exchange);
    }

    var payload = builder.build().toByteArray();

    try {
      this.channel.basicPublish(
        getGroupExchange(this.exchange),
        getTextQueue(this.routingKey),
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
        .setSender(this.userName)
        .setBody(ByteString.copyFrom(content))
        .setType(type)
        .setFilename(filename);

      if (!this.exchange.isEmpty()) {
        builder = builder.setGroup(this.exchange);
      }

      var payload = builder.build().toByteArray();

      String destination = String.format(
        this.routingKey.isBlank()
          ? "group=" + this.exchange
          : "user=" + this.routingKey
      );
      try {
        this.channel.basicPublish(
          getGroupExchange(this.exchange),
          getFileQueue(this.routingKey),
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
