package br.ufs.dcomp.ChatRabbitMQ;

class ChatCommand {

  protected final String description;
  protected final String[] keywords;
  protected final ChatConsumer<String[]> action;

  ChatCommand(
    String[] keywords,
    String description,
    ChatConsumer<String[]> action
  ) {
    this.description = description;
    this.keywords = keywords;
    this.action = action;
  }

  public void apply(String[] args) {
    if (args.length != this.keywords.length) {
      System.err.println("Usage: " + getUsage());
      return;
    }

    try {
      this.action.accept(args);
    } catch (final ChatException e) {
      System.err.println(e.getMessage());
    }
  }

  public String getName() {
    return this.keywords[0];
  }

  public String getUsage() {
    String usage = ChatSymbol.COMMAND_SYMBOL + this.keywords[0];
    for (var i = 1; i < this.keywords.length; ++i) {
      usage += " <" + this.keywords[i] + ">";
    }
    usage += "\n\t" + this.description;
    return usage;
  }
}
