package br.ufs.dcomp.ChatRabbitMQ;

class ChatSimpleCommand extends ChatCommand {

  private final Character symbol;

  ChatSimpleCommand(
    String[] keywords,
    String description,
    ChatConsumer<String[]> action,
    Character symbol
  ) {
    super(keywords, description, action);
    this.symbol = symbol;
  }

  public String getName() {
    return this.symbol.toString();
  }

  public Character getSymbol() {
    return this.symbol;
  }

  public void apply(String[] args) {
    if (
      args.length != this.keywords.length &&
      !this.keywords[this.keywords.length - 1].equals(ChatSymbol.VARARG_TEXT)
    ) {
      System.err.println("Usage: " + getUsage());
      return;
    }

    try {
      this.action.accept(args);
    } catch (final ChatException e) {
      System.err.println(e.getMessage());
    }
  }

  public String getUsage() {
    String usage = this.symbol.toString();
    for (var i = 0; i < this.keywords.length; ++i) {
      usage += "<" + this.keywords[i] + "> ";
    }
    usage += "\n\t" + this.description;
    return usage;
  }
}
