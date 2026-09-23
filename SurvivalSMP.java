package com.survivalsmp;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import java.util.*;
import org.bukkit.scoreboard.*;
import org.bukkit.event.player.PlayerQuitEvent;

public class SurvivalSMP extends JavaPlugin implements Listener {
    private final Map<UUID,Double> money=new HashMap<>();
    private final Map<UUID,Long> playtime=new HashMap<>();
    private NamespacedKey weaponKey;
    private FileConfiguration data;
    private final Map<UUID, Long> claimedReward = new HashMap<>();
    private static final int[] REWARD_MINUTES = {30, 60, 120, 300, 600, 1200};
    private static final double[] REWARD_MONEY = {100, 250, 600, 1500, 4000, 10000};
    private static final String SHOP="§8§l✦ §bSMP MARKET §8§l✦";
    private static final String WEAPON="§8§l✦ §5CUSTOM WEAPONS §8§l✦";

    public void onEnable(){
        weaponKey=new NamespacedKey(this,"weapon_id");
        getServer().getPluginManager().registerEvents(this,this);
        for(String c:new String[]{"bal","balance","money","shop","sell","sellall","giveweapon"})
            if(getCommand(c)!=null)getCommand(c).setExecutor(new Commands());

        if(!getDataFolder().exists()) getDataFolder().mkdirs();
        File f=new File(getDataFolder(),"data.yml");
        data=YamlConfiguration.loadConfiguration(f);
        loadData();

        // Playtime, reward checks and sidebar refresh.
        getServer().getScheduler().runTaskTimer(this,()->{
            for(Player p:Bukkit.getOnlinePlayers()){
                playtime.merge(p.getUniqueId(),1L,Long::sum);
                checkRewards(p);
                updateBoard(p);
            }
            saveData();
        },1200L,1200L);
    }

    @Override public void onDisable(){ saveData(); }

    private void loadData(){
        if(data==null)return;
        for(String s:data.getStringList("players")) {
            try {
                UUID id=UUID.fromString(s);
                money.put(id,data.getDouble("money."+s,0));
                playtime.put(id,data.getLong("playtime."+s,0));
                claimedReward.put(id,data.getLong("claimed."+s,0));
            } catch(Exception ignored){}
        }
    }
    private void saveData(){
        if(data==null)return;
        List<String> players=new ArrayList<>();
        Set<UUID> ids=new HashSet<>(); ids.addAll(money.keySet()); ids.addAll(playtime.keySet());
        for(UUID id:ids){
            String s=id.toString(); players.add(s);
            data.set("money."+s,money.getOrDefault(id,0D));
            data.set("playtime."+s,playtime.getOrDefault(id,0L));
            data.set("claimed."+s,claimedReward.getOrDefault(id,0L));
        }
        data.set("players",players);
        try{data.save(new File(getDataFolder(),"data.yml"));}catch(Exception ex){getLogger().warning("Could not save data.yml: "+ex.getMessage());}
    }

    private void checkRewards(Player p){
        long mins=playtime.getOrDefault(p.getUniqueId(),0L);
        long claimed=claimedReward.getOrDefault(p.getUniqueId(),0L);
        for(int i=0;i<REWARD_MINUTES.length;i++){
            if(mins>=REWARD_MINUTES[i] && claimed<i+1){
                double reward=REWARD_MONEY[i];
                add(p,reward);
                claimedReward.put(p.getUniqueId(),(long)i+1);
                msg(p,"&a✦ PLAYTIME REWARD &8» &6+$"+(long)reward+" &7for reaching &e"+formatMinutes(REWARD_MINUTES[i])+"&7!");
            }
        }
    }
    private String formatMinutes(long m){ return m>=60 ? (m/60)+"h "+(m%60)+"m" : m+"m"; }

    private void updateBoard(Player p){
        ScoreboardManager sm=Bukkit.getScoreboardManager(); if(sm==null)return;
        Scoreboard b=sm.getNewScoreboard();
        Objective o=b.registerNewObjective("smp","dummy",Component.text(c("&b&l✦ SMP SERVER")));
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        o.getScore(c("&7&m----------------")).setScore(8);
        o.getScore(c("&fBalance")).setScore(7);
        o.getScore(c("&6$"+(long)bal(p))).setScore(6);
        o.getScore(c("&fPlaytime")).setScore(5);
        o.getScore(c("&b"+formatMinutes(playtime.getOrDefault(p.getUniqueId(),0L)))).setScore(4);
        o.getScore(c("&fTop Players")).setScore(3);
        List<UUID> ids=new ArrayList<>(playtime.keySet());
        ids.sort((a,b2)->Long.compare(playtime.getOrDefault(b2,0L),playtime.getOrDefault(a,0L)));
        int shown=0, score=2;
        for(UUID id:ids){
            if(shown>=3)break;
            OfflinePlayer op=Bukkit.getOfflinePlayer(id);
            o.getScore(c("&e"+(shown+1)+". &f"+op.getName())).setScore(score--); shown++;
        }
        p.setScoreboard(b);
    }

    private String c(String s){return ChatColor.translateAlternateColorCodes('&',s);}
    private void msg(Player p,String s){p.sendMessage(c(s));}
    private ItemStack gui(Material m,String name,String... lore){
        ItemStack i=new ItemStack(m); ItemMeta meta=i.getItemMeta();
        meta.displayName(Component.text(c(name)));
        List<Component> ls=new ArrayList<>(); for(String x:lore)ls.add(Component.text(c(x))); meta.lore(ls);
        i.setItemMeta(meta); return i;
    }
    private ItemStack weaponItem(int id){
        Material m; String n;
        switch(id){
            case 1001 -> {m=Material.IRON_SWORD;n="&bWind Katana";}
            case 1002 -> {m=Material.MACE;n="&eThunder Hammer";}
            case 1003 -> {m=Material.GOLDEN_SWORD;n="&4Vampire Dagger";}
            case 1004 -> {m=Material.NETHERITE_SWORD;n="&cDemon Slayer Katana";}
            default -> throw new IllegalArgumentException();
        }
        ItemStack i=gui(m,n,"&7Custom Weapon","&8ID: &f"+id);
        ItemMeta meta=i.getItemMeta();
        meta.getPersistentDataContainer().set(weaponKey,PersistentDataType.INTEGER,id);
        // Minecraft 26.1.x: use the structured CustomModelData component.
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setFloats(List.of((float) id));
        meta.setCustomModelDataComponent(cmd);
        i.setItemMeta(meta); return i;
    }
    private int weapon(ItemStack i){
        if(i==null||i.getType().isAir()||!i.hasItemMeta())return 0;
        Integer id=i.getItemMeta().getPersistentDataContainer().get(weaponKey,PersistentDataType.INTEGER);
        if(id!=null)return id;
        try {
            ItemMeta meta = i.getItemMeta();
            if (meta.hasCustomModelDataComponent()) {
                CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
                if (!cmd.getFloats().isEmpty()) return Math.round(cmd.getFloats().get(0));
            }
        } catch (Throwable ignored) {}
        return 0;
    }
    private double bal(Player p){return money.getOrDefault(p.getUniqueId(),0D);}
    private void add(Player p,double n){money.put(p.getUniqueId(),bal(p)+n);}

    private void shop(Player p){
        Inventory inv=Bukkit.createInventory(null,54,Component.text(SHOP));
        // Decorative borders
        ItemStack pane=gui(Material.GRAY_STAINED_GLASS_PANE,"&7");
        for(int i=0;i<54;i++)if(i<9||i>44||i%9==0||i%9==8)inv.setItem(i,pane);
        inv.setItem(10,gui(Material.OAK_LOG,"&a&lBUILDING","&7Oak Logs ×64","&6$20","&8Click to purchase"));
        inv.setItem(12,gui(Material.IRON_INGOT,"&f&lMINERALS","&7Iron Ingots ×64","&6$50","&8Click to purchase"));
        inv.setItem(14,gui(Material.DIAMOND,"&b&lDIAMONDS","&7Diamonds ×32","&6$200","&8Click to purchase"));
        inv.setItem(16,gui(Material.GOLDEN_APPLE,"&d&lGOLDEN APPLE","&7Golden Apple ×1","&6$30","&8Click to purchase"));
        inv.setItem(28,weaponShopIcon(1001,"&bWind Katana","&6$500"));
        inv.setItem(30,weaponShopIcon(1003,"&4Vampire Dagger","&6$750"));
        inv.setItem(32,weaponShopIcon(1002,"&eThunder Hammer","&6$1,000"));
        inv.setItem(34,weaponShopIcon(1004,"&cDemon Slayer Katana","&6$1,500"));
        inv.setItem(49,gui(Material.PAPER,"&e&lYOUR BALANCE","&6$"+(long)bal(p)));
        p.openInventory(inv);
    }
    private ItemStack weaponShopIcon(int id,String name,String price){
        ItemStack i=weaponItem(id); ItemMeta m=i.getItemMeta();
        m.displayName(Component.text(c(name)));
        m.lore(List.of(Component.text(c("&7Custom weapon")),Component.text(c(price)),Component.text(c("&8Click to purchase"))));
        i.setItemMeta(m); return i;
    }

    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        if(!e.getView().getTitle().equals(SHOP))return;
        if(e.getRawSlot()<0||e.getRawSlot()>=e.getView().getTopInventory().getSize())return;
        e.setCancelled(true); int s=e.getRawSlot();
        if(s==10)buy(p,20,new ItemStack(Material.OAK_LOG,64));
        else if(s==12)buy(p,50,new ItemStack(Material.IRON_INGOT,64));
        else if(s==14)buy(p,200,new ItemStack(Material.DIAMOND,32));
        else if(s==16)buy(p,30,new ItemStack(Material.GOLDEN_APPLE,1));
        else if(s==28)buyWeapon(p,1001,500);
        else if(s==30)buyWeapon(p,1003,750);
        else if(s==32)buyWeapon(p,1002,1000);
        else if(s==34)buyWeapon(p,1004,1500);
    }
    private void buy(Player p,double price,ItemStack item){
        if(bal(p)<price){msg(p,"&c✘ Not enough money. Balance: &6$"+(long)bal(p));return;}
        add(p,-price); p.getInventory().addItem(item); msg(p,"&a✔ Purchase complete! &7-$"+(long)price);
    }
    private void buyWeapon(Player p,int id,double price){
        if(bal(p)<price){msg(p,"&c✘ Not enough money. Balance: &6$"+(long)bal(p));return;}
        add(p,-price); p.getInventory().addItem(weaponItem(id)); msg(p,"&a✔ Custom weapon purchased!");
    }

    @EventHandler public void interact(PlayerInteractEvent e){
        if(e.getAction()!=Action.RIGHT_CLICK_AIR&&e.getAction()!=Action.RIGHT_CLICK_BLOCK)return;
        Player p=e.getPlayer(); int id=weapon(p.getInventory().getItemInMainHand()); if(id==0)return;
        switch(id){
            case 1001 -> {msg(p,"&b⚡ Wind Burst!");p.setVelocity(p.getLocation().getDirection().multiply(1.2).setY(.25));}
            case 1003 -> {msg(p,"&4☠ Dark Magic!");effect(p,PotionEffectType.REGENERATION,1,60);}
            case 1002 -> {msg(p,"&e⚡ Thunder Strike!");Block b=p.getTargetBlockExact(30);if(b!=null)p.getWorld().strikeLightning(b.getLocation().add(0,1,0));}
            case 1004 -> {msg(p,"&c🔥 Flame Breathing!");effect(p,PotionEffectType.FIRE_RESISTANCE,0,100);}
        }
    }
    @EventHandler public void join(org.bukkit.event.player.PlayerJoinEvent e){
        money.putIfAbsent(e.getPlayer().getUniqueId(),0D);
        playtime.putIfAbsent(e.getPlayer().getUniqueId(),0L);
        claimedReward.putIfAbsent(e.getPlayer().getUniqueId(),0L);
        updateBoard(e.getPlayer());
    }
    @EventHandler public void quit(PlayerQuitEvent e){ saveData(); }

    @EventHandler public void damage(EntityDamageByEntityEvent e){
        if(!(e.getDamager() instanceof Player p))return; int id=weapon(p.getInventory().getItemInMainHand()); if(id==0)return;
        if(id==1001&&e.getEntity() instanceof LivingEntity v)v.setVelocity(v.getVelocity().setY(.5));
        else if(id==1003&&e.getEntity() instanceof LivingEntity v)effect(v,PotionEffectType.WITHER,0,40);
        else if(id==1002)e.getEntity().getWorld().strikeLightning(e.getEntity().getLocation());
        else if(id==1004&&e.getEntity() instanceof LivingEntity v)v.setFireTicks(60);
    }
    private void effect(LivingEntity e,PotionEffectType t,int amp,int ticks){e.addPotionEffect(new PotionEffect(t,ticks,amp,true,false));}

    private void sell(Player p,boolean all){
        double total=0; PlayerInventory inv=p.getInventory();
        if(!all){
            ItemStack t=inv.getItemInMainHand();
            if(t.getType()==Material.DIAMOND){total=t.getAmount()*50;inv.setItemInMainHand(null);}
            else if(t.getType()==Material.GOLD_INGOT){total=t.getAmount()*20;inv.setItemInMainHand(null);}
            else {msg(p,"&cThis item cannot be sold.");return;}
        }else for(int s=0;s<inv.getSize();s++){ItemStack t=inv.getItem(s);if(t==null)continue;
            if(t.getType()==Material.DIAMOND){total+=t.getAmount()*50;inv.setItem(s,null);}
            else if(t.getType()==Material.GOLD_INGOT){total+=t.getAmount()*20;inv.setItem(s,null);}
        }
        if(total<=0){msg(p,"&cNo sellable items found.");return;} add(p,total);msg(p,"&a✔ Sold for &6$"+(long)total+"&a!");
    }

    private void showLeaderboard(Player p){
        List<UUID> ids=new ArrayList<>(playtime.keySet());
        ids.sort((a,b)->Long.compare(playtime.getOrDefault(b,0L),playtime.getOrDefault(a,0L)));
        msg(p,"&8&m--------------------------------");
        msg(p,"&b&l        PLAYTIME LEADERBOARD");
        int rank=1;
        for(UUID id:ids){
            if(rank>10)break;
            OfflinePlayer op=Bukkit.getOfflinePlayer(id);
            msg(p,"&e#"+rank+" &f"+(op.getName()==null?"Unknown":op.getName())+" &8» &b"+formatMinutes(playtime.getOrDefault(id,0L)));
            rank++;
        }
        msg(p,"&8&m--------------------------------");
    }

    private class Commands implements CommandExecutor{
        public boolean onCommand(CommandSender s,Command cmd,String label,String[] a){
            if(cmd.getName().equalsIgnoreCase("giveweapon")){
                if(!s.hasPermission("survivalsmp.admin")){s.sendMessage("§cNo permission.");return true;}
                if(a.length<2){s.sendMessage("§e/giveweapon <player> <wind|vampire|thunder|demon>");return true;}
                Player target=Bukkit.getPlayerExact(a[0]); if(target==null){s.sendMessage("§cPlayer not found.");return true;}
                int id=switch(a[1].toLowerCase()){case "wind","windkatana"->1001;case "vampire","dagger","vampiredagger"->1003;case "thunder","hammer","thunderhammer"->1002;case "demon","demonslayer","katana"->1004;default->0;};
                if(id==0){s.sendMessage("§cUnknown weapon.");return true;} target.getInventory().addItem(weaponItem(id));s.sendMessage("§aGiven custom weapon to "+target.getName()+".");return true;
            }
            if(!(s instanceof Player p))return true;
            switch(cmd.getName().toLowerCase()){
                case "bal","balance","money"->msg(p,"&eBalance: &6$"+(long)bal(p));
                case "shop"->shop(p);
                case "sell"->sell(p,false);
                case "sellall"->sell(p,true);
                case "playtime"->msg(p,"&bYour playtime: &e"+formatMinutes(playtime.getOrDefault(p.getUniqueId(),0L)));
                case "leaderboard"->showLeaderboard(p);
            }return true;
        }
    }
}
