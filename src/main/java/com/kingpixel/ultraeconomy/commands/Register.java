package com.kingpixel.ultraeconomy.commands;

import com.kingpixel.ultraeconomy.UltraEconomy;
import com.kingpixel.ultraeconomy.commands.admin.*;
import com.kingpixel.ultraeconomy.config.Currencies;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

/**
 * @author Carlos Varas Alonso - 23/09/2025 21:29
 */
public class Register {
  public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess,
                              CommandManager.RegistrationEnvironment environment) {
    for (String command : UltraEconomy.config.getCommands()) {
      var base = CommandManager.literal(command);
      base.executes(context -> {
        BalanceCommand.run(context.getSource().getPlayer(), context.getSource(), Currencies.DEFAULT_CURRENCY.getId());
        return 1;
      });

      PayCommand.put(dispatcher, base);
      BalanceCommand.put(dispatcher, base);
      ReloadCommand.put(dispatcher, base);
      DepositCommand.put(dispatcher, base);
      BaltopCommand.put(dispatcher, base);

      dispatcher.register(base);
    }
  }
}
