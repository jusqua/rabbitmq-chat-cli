package br.ufs.dcomp.ChatRabbitMQ;

public class ChatSymbol {

  public static final String PROMPT_TEXT = ">> ";
  public static final String LOGIN_TEXT = "<< ";
  public static final String VARARG_TEXT = "...";

  public static final Character TEXT_SYMBOL = "$".charAt(0);
  public static final Character FILE_SYMBOL = "!".charAt(0);
  public static final Character GROUP_SYMBOL = "@".charAt(0);
  public static final Character USER_SYMBOL = "#".charAt(0);
  public static final Character COMMAND_SYMBOL = "/".charAt(0);
  public static final Character[] COMMAND_SYMBOLS = new Character[] {
    TEXT_SYMBOL,
    FILE_SYMBOL,
    GROUP_SYMBOL,
    USER_SYMBOL,
    COMMAND_SYMBOL,
  };
}
