package br.ufs.dcomp.ChatRabbitMQ;

import com.rabbitmq.client.*;

import java.util.Scanner;

import java.io.IOException;

public class Chat {

  public static void main(String[] argv) throws Exception {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("13.218.246.166");
    factory.setUsername("admin");
    Scanner sc = new Scanner(System.in);
    factory.setPassword("password");
    factory.setVirtualHost("/");
    Connection connection = factory.newConnection();
    Channel channel = connection.createChannel();
    String usuario = "";
    String destinatario = "";
    
      
    System.out.print("\033[H\033[2J");
    System.out.print("User:");
    usuario = sc.nextLine();
    System.out.println(usuario);
    
    channel.queueDeclare(usuario, false,   false,     false,       null);
    
    Consumer consumer = new DefaultConsumer(channel) {
      public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body)           throws IOException {

        String message = new String(body, "UTF-8");

      }
    };
    channel.basicConsume(usuario, true, consumer);
    
    
    Integer i = 0; 
    
    while (i == 0){
      System.out.print("\033[H\033[2J");
      System.out.print("<<");
      String destinatario_parcial = sc.nextLine();
      if (destinatario_parcial.charAt(0) == '@'){
        System.out.print("\033[H\033[2J");
        i = 1;
        destinatario = destinatario_parcial.substring(1);
      }else{
        System.out.println("Digite o destinatário no seguinte formato: @userdodestinatario");
      }
    
    }
    System.out.println(destinatario);

  }
}