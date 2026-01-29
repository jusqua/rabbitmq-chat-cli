package br.ufs.dcomp.ChatRabbitMQ;

import java.util.HashMap;

public class ChatCommandHandler {

  private final ChatSimpleCommand DEFAULT_COMMAND;
  private HashMap<String, ChatCommand> commandMap;
  private HashMap<Character, ChatSimpleCommand> simpleCommandMap;

  ChatCommandHandler(Chat chat) {
    this.commandMap = new HashMap<String, ChatCommand>();
    this.simpleCommandMap = new HashMap<Character, ChatSimpleCommand>();
    DEFAULT_COMMAND = new ChatSimpleCommand(
      new String[] { ChatSymbol.VARARG_TEXT },
      "Used when command does not find the keyword",
      args -> {
        System.err.printf("\"%s\" is not a valid command\n", args[0]);
      },
      " ".charAt(0)
    );

    appendToMap(
      new ChatSimpleCommand(
        new String[] { "user-name" },
        "Set given user as the current destinatary",
        args -> {
          chat.setDestinatary(args[0], false);
        },
        ChatSymbol.USER_SYMBOL
      )
    );
    appendToMap(
      new ChatSimpleCommand(
        new String[] { "group-name" },
        "Set given group as the current destinatary",
        args -> {
          chat.setDestinatary(args[0], true);
        },
        ChatSymbol.GROUP_SYMBOL
      )
    );
    appendToMap(
      new ChatSimpleCommand(
        new String[] { "path-to-file" },
        "Send given file up to 16MB to the current destinatary",
        args -> {
          if (!chat.hasDestinatary()) {
            System.err.println("No destinatary has been specified");
            return;
          }
          chat.sendFile(args[0]);
        },
        ChatSymbol.FILE_SYMBOL
      )
    );
    appendToMap(
      new ChatSimpleCommand(
        new String[] { "text-message", ChatSymbol.VARARG_TEXT },
        "Send given message to the current destinatary",
        args -> {
          if (!chat.hasDestinatary()) {
            System.err.println("No destinatary has been specified");
            return;
          }
          chat.sendText(String.join(" ", args));
        },
        ChatSymbol.TEXT_SYMBOL
      )
    );

    appendToMap(
      new ChatCommand(
        new String[] { "create-group", "group-name" },
        "Create a chat group",
        args -> {
          chat.createGroup(args[1]);
        }
      )
    );
    appendToMap(
      new ChatCommand(
        new String[] { "delete-group", "group-name" },
        "Delete an existing chat group that you is part of",
        args -> {
          chat.createGroup(args[1]);
        }
      )
    );
    appendToMap(
      new ChatCommand(
        new String[] { "invite-user", "user-name", "group-name" },
        "Invite user to join the current group",
        args -> {
          chat.addUserToGroup(args[1], args[2]);
        }
      )
    );
    appendToMap(
      new ChatCommand(
        new String[] { "kick-user", "user-name", "group-name" },
        "Kick the user who is participating in the current group",
        args -> {
          chat.removeUserFromGroup(args[1], args[2]);
        }
      )
    );
    appendToMap(
      new ChatCommand(
        new String[] { "leave", "group-name" },
        "Leave current group",
        args -> {
          chat.leaveGroup(args[1]);
        }
      )
    );
    appendToMap(
      new ChatCommand(
        new String[] { "list-users", "group-name" },
        "List the users who are participating in the current group",
        args -> {
          var list = chat.listUsers(args[1]);
          System.out.printf(
            "%c%s user count: %d\n",
            ChatSymbol.GROUP_SYMBOL,
            args[1],
            list.size()
          );
          for (var u : list) {
            System.out.print(ChatSymbol.USER_SYMBOL + u + " ");
          }
          if (list.size() > 0) {
            System.out.println();
          }
        }
      )
    );
    appendToMap(
      new ChatCommand(
        new String[] { "list-groups" },
        "List the groups you are participating",
        args -> {
          var list = chat.listGroups();
          System.out.printf(
            "%c%s group count: %d\n",
            ChatSymbol.USER_SYMBOL,
            chat.getUserName(),
            list.size()
          );
          for (var g : list) {
            System.out.print(ChatSymbol.GROUP_SYMBOL + g + " ");
          }
          if (list.size() > 0) {
            System.out.println();
          }
        }
      )
    );
    appendToMap(
      new ChatCommand(
        new String[] { "help" },
        "Desconnect yourself from the server",
        args -> {
          for (var entry : this.simpleCommandMap.entrySet()) {
            System.out.println(entry.getValue().getUsage());
          }
          for (var entry : this.commandMap.entrySet()) {
            System.out.println(entry.getValue().getUsage());
          }
        }
      )
    );
    appendToMap(
      new ChatCommand(
        new String[] { "logout" },
        "Desconnect yourself from the server",
        args -> {
          chat.logOut();
        }
      )
    );
    appendToMap(
      new ChatCommand(
        new String[] { "exit" },
        "Close the chat connection",
        args -> {
          try {
            chat.close();
          } catch (final Exception e) {
            System.err.println(e.getMessage());
          }
        }
      )
    );
  }

  private void appendToMap(ChatCommand command) {
    this.commandMap.put(command.getName(), command);
  }

  private void appendToMap(ChatSimpleCommand command) {
    this.simpleCommandMap.put(command.getSymbol(), command);
  }

  public void apply(Character symbol, String args[]) {
    if (symbol.equals(ChatSymbol.COMMAND_SYMBOL)) {
      this.commandMap.getOrDefault(args[0], DEFAULT_COMMAND).apply(args);
    } else {
      this.simpleCommandMap.getOrDefault(symbol, DEFAULT_COMMAND).apply(args);
    }
  }
}
