package com.kingpixel.ultraeconomy;

import com.kingpixel.cobbleutils.Model.DataBaseConfig;
import com.kingpixel.ultraeconomy.database.DatabaseFactory;
import com.kingpixel.ultraeconomy.models.Account;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;

public class Ultraeconomy implements ModInitializer {

  @Override
  public void onInitialize() {
    load();
    events();
  }

  public void load() {
    DatabaseFactory.init(new DataBaseConfig());
  }

  public void events() {
    ServerPlayerEvents.JOIN.register((player) -> {
      Account account = DatabaseFactory.INSTANCE.getAccount(player.getUuid());
      DatabaseFactory.INSTANCE.saveOrUpdateAccount(account);
    });

    ServerPlayerEvents.LEAVE.register((player) -> {
      DatabaseFactory.accounts.invalidateAll();
    });
  }

}
