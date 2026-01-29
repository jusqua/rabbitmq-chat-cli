package br.ufs.dcomp.ChatRabbitMQ;

@FunctionalInterface
public interface ChatConsumer<T> {
  void accept(T t) throws ChatException;
}
