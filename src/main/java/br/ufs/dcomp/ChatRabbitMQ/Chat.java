package br.ufs.dcomp.ChatRabbitMQ;

import br.ufs.dcomp.Message;
import com.google.protobuf.ByteString;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public class Chat implements AutoCloseable {

  private boolean isActive;
  private Connection connection;
  private Channel channel;

  private String currentUser;
  private String currentDestinatary;
  private String currentGroup;

  Chat(
    String host,
    String username,
    Function<Channel, Consumer> consumerFactory
  ) throws IOException, TimeoutException {
    var connectionFactory = new ConnectionFactory();
    connectionFactory.setHost(host);
    this.connection = connectionFactory.newConnection();
    this.channel = connection.createChannel();

    this.currentUser = username;
    this.currentDestinatary = "";
    this.currentGroup = "";
    this.channel.queueDeclare(username, false, false, false, null);
    this.channel.basicConsume(username, true, consumerFactory.apply(channel));
    this.isActive = true;
  }

  @Override
  public void close() throws IOException, TimeoutException {
    this.channel.close();
    this.connection.close();
    this.isActive = false;
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
      tmp.queueDeclarePassive(userName);
      tmp.close();
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public boolean getIsActive() {
    return this.isActive;
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

  public void createGroup(String groupName) throws ChatException {
    if (isGroupExists(groupName)) {
      throw new ChatException("Given group already exist");
    }

    try {
      this.channel.exchangeDeclare(groupName, "fanout");
      this.channel.queueBind(this.currentUser, groupName, "");
    } catch (final Exception e) {
      throw new ChatException("Could not create group");
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

  public void leaveGroup(String groupName) throws ChatException {
    if (!isGroupExists(groupName)) {
      throw new ChatException("Given group does not exist");
    }

    try {
      this.channel.queueUnbind(currentUser, groupName, "");
    } catch (final Exception e) {
      throw new ChatException("Could not leave group");
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
      this.channel.queueBind(userName, groupName, "");
    } catch (final Exception e) {
      throw new ChatException("Could not add user to group");
    }
  }

  public void removeUserToGroup(String userName, String groupName)
    throws ChatException {
    if (!isUserExists(userName)) {
      throw new ChatException("Given user does not exist");
    } else if (!isGroupExists(groupName)) {
      throw new ChatException("Given group does not exist");
    }

    try {
      this.channel.queueUnbind(userName, groupName, "");
    } catch (final Exception e) {
      throw new ChatException("Could not remove user from group");
    }
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

  public void sendMessage(String text) throws ChatException {
    var builder = Message.newBuilder()
      .setSender(currentUser)
      .setBody(ByteString.copyFromUtf8(text))
      .setDatetime(
        LocalDateTime.now().format(
          DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
        )
      );

    if (!currentGroup.isEmpty()) {
      builder = builder.setGroup(currentGroup);
    }

    var payload = builder.build().toByteArray();

    try {
      this.channel.basicPublish(
        currentGroup,
        currentDestinatary,
        null,
        payload
      );
    } catch (final Exception e) {
      throw new ChatException("Could not send message");
    }
  }
}
