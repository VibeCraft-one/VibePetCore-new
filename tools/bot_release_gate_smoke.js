const mineflayer = require(process.env.MINEFLAYER_PATH || 'mineflayer');

const host = process.env.MC_HOST || '127.0.0.1';
const port = Number(process.env.MC_PORT || '25569');
const username = process.env.MC_USER || 'SmokeGateBot';
const version = process.env.MC_VERSION || '1.21.11';

function log(type, detail = {}) {
  console.log(JSON.stringify({ t: new Date().toISOString(), type, ...detail }));
}

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function waitForEvent(emitter, event, timeoutMs) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      cleanup();
      reject(new Error(`timeout:${event}`));
    }, timeoutMs);

    function cleanup() {
      clearTimeout(timer);
      emitter.removeListener(event, onEvent);
      emitter.removeListener('end', onEnd);
      emitter.removeListener('kicked', onKicked);
      emitter.removeListener('error', onError);
    }

    function onEvent(...args) {
      cleanup();
      resolve(args);
    }

    function onEnd(reason) {
      cleanup();
      reject(new Error(`ended-before-${event}:${reason}`));
    }

    function onKicked(reason) {
      cleanup();
      reject(new Error(`kicked-before-${event}:${reason}`));
    }

    function onError(error) {
      cleanup();
      reject(error);
    }

    emitter.once(event, onEvent);
    emitter.once('end', onEnd);
    emitter.once('kicked', onKicked);
    emitter.once('error', onError);
  });
}

function createChatWaiter(bot, timeoutMs, predicate, label) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      cleanup();
      reject(new Error(`timeout:chat:${label}`));
    }, timeoutMs);

    function cleanup() {
      clearTimeout(timer);
      bot.removeListener('message', onMessage);
      bot.removeListener('end', onEnd);
      bot.removeListener('kicked', onKicked);
      bot.removeListener('error', onError);
    }

    function onMessage(jsonMsg, position) {
      const text = jsonMsg.toString();
      if (predicate(text, position)) {
        cleanup();
        resolve({ text, position });
      }
    }

    function onEnd(reason) {
      cleanup();
      reject(new Error(`ended-before-chat:${label}:${reason}`));
    }

    function onKicked(reason) {
      cleanup();
      reject(new Error(`kicked-before-chat:${label}:${reason}`));
    }

    function onError(error) {
      cleanup();
      reject(error);
    }

    bot.on('message', onMessage);
    bot.once('end', onEnd);
    bot.once('kicked', onKicked);
    bot.once('error', onError);
  });
}

function windowTitle(win) {
  if (!win) {
    return null;
  }
  if (typeof win.title === 'string') {
    return win.title;
  }
  try {
    return JSON.stringify(win.title);
  } catch {
    return String(win.title);
  }
}

function windowSummary(win) {
  return {
    id: win?.id ?? null,
    type: win?.type ?? null,
    title: windowTitle(win),
    slots: win?.slots?.length ?? null
  };
}

function inventorySummary(bot) {
  return bot.inventory.items().map(item => ({
    slot: item.slot,
    name: item.name,
    count: item.count,
    displayName: item.displayName
  }));
}

function countItems(bot, predicate) {
  return bot.inventory.items().filter(predicate).reduce((sum, item) => sum + item.count, 0);
}

function countEggs(bot) {
  return countItems(bot, item => item.name.endsWith('_spawn_egg'));
}

function countWolfEggs(bot) {
  return countItems(bot, item => item.name === 'wolf_spawn_egg');
}

async function command(bot, text, waitMs = 1400) {
  log('command', { text });
  bot.chat(text);
  await delay(waitMs);
}

async function openByCommand(bot, text, label) {
  const wait = waitForEvent(bot, 'windowOpen', 8000);
  log('openCommand', { label, text });
  bot.chat(text);
  const [win] = await wait;
  log('windowOpen', { label, window: windowSummary(win) });
  await delay(500);
  return win;
}

async function clickSlot(bot, slot, label, waitMs = 700) {
  const win = bot.currentWindow;
  if (!win) {
    throw new Error(`no current window for ${label}`);
  }
  const item = win.slots?.[slot];
  log('clickSlot', {
    label,
    slot,
    item: item ? { name: item.name, count: item.count, displayName: item.displayName } : null,
    window: windowSummary(win)
  });
  await bot.clickWindow(slot, 0, 0);
  await delay(waitMs);
}

async function clickSlotWaitWindow(bot, slot, label) {
  const wait = waitForEvent(bot, 'windowOpen', 8000);
  await clickSlot(bot, slot, label, 200);
  const [win] = await wait;
  log('windowOpen', { label, window: windowSummary(win) });
  await delay(500);
  return win;
}

async function closeWindow(bot, label) {
  if (!bot.currentWindow) {
    return;
  }
  log('closeWindow', { label, window: windowSummary(bot.currentWindow) });
  bot.closeWindow(bot.currentWindow);
  await delay(500);
}

function nearestBlock(bot, name, radius = 6) {
  const origin = bot.entity.position.floored();
  let best = null;
  for (let x = -radius; x <= radius; x++) {
    for (let y = -radius; y <= radius; y++) {
      for (let z = -radius; z <= radius; z++) {
        const block = bot.blockAt(origin.offset(x, y, z));
        if (!block || block.name !== name) {
          continue;
        }
        const distance = block.position.distanceTo(bot.entity.position);
        if (!best || distance < best.distance) {
          best = { block, distance };
        }
      }
    }
  }
  return best?.block ?? null;
}

async function openSourceByBlock(bot) {
  const sourceBlock = nearestBlock(bot, 'conduit', 6);
  if (!sourceBlock) {
    throw new Error('source conduit block not found');
  }
  const wait = waitForEvent(bot, 'windowOpen', 8000);
  log('activateSourceBlock', { position: sourceBlock.position.toString() });
  await bot.activateBlock(sourceBlock);
  const [win] = await wait;
  log('windowOpen', { label: 'source-block-open', window: windowSummary(win) });
  await delay(500);
  return win;
}

async function ensureNoWolfYet(bot) {
  await delay(1800);
  const wolf = Object.values(bot.entities).find(entity => entity.name === 'wolf');
  if (wolf) {
    throw new Error('offhand source click unexpectedly summoned wolf');
  }
}

async function giveGatherResources(bot) {
  const resources = [
    ['bread', 64],
    ['salmon', 64],
    ['raw_iron', 64],
    ['amethyst_shard', 64],
    ['honey_bottle', 32],
    ['coal', 128],
    ['raw_copper', 64],
    ['redstone', 128],
    ['lapis_lazuli', 64],
    ['diamond', 16]
  ];
  for (const [itemName, amount] of resources) {
    await command(bot, `/give ${username} ${itemName} ${amount}`, 1400);
  }
}

async function questGuiTurnIn(bot) {
  await openByCommand(bot, '/pet menu main', 'source-main-quest');
  await clickSlotWaitWindow(bot, 10, 'source-main-open-quests');
  await clickSlotWaitWindow(bot, 5, 'quest-tab-gather');
  const firstQuest = bot.currentWindow?.slots?.[10];
  log('questSlot10BeforeAccept', {
    item: firstQuest ? { name: firstQuest.name, count: firstQuest.count, displayName: firstQuest.displayName } : null
  });
  await clickSlotWaitWindow(bot, 10, 'quest-accept-slot10');
  await closeWindow(bot, 'quest-after-accept');

  await giveGatherResources(bot);
  await command(bot, '/pet points');

  await openByCommand(bot, '/pet menu main', 'source-main-quest-turnin');
  await clickSlotWaitWindow(bot, 10, 'source-main-open-quests-turnin');
  await clickSlotWaitWindow(bot, 5, 'quest-tab-gather-turnin');
  const turnInChat = createChatWaiter(
    bot,
    8000,
    text => text.includes('Quest') || text.includes('Квест') || text.includes('очки питомца') || text.includes('Pet Points'),
    'quest-turnin'
  ).catch(error => ({ error: error.message }));
  await clickSlotWaitWindow(bot, 10, 'quest-turnin-slot10');
  const turnInResult = await turnInChat;
  log('questTurnInResult', turnInResult);
  await command(bot, '/pet points');
  await closeWindow(bot, 'quest-after-turnin');
}

async function forgeGuiAttempt(bot) {
  await command(bot, `/vpc admin giveegg ${username} wolf common`, 2200);
  await command(bot, `/vpc admin giveegg ${username} wolf common`, 2200);
  const beforeWolfEggs = countWolfEggs(bot);
  log('forgeBefore', { beforeWolfEggs, items: inventorySummary(bot) });

  await openByCommand(bot, '/pet menu main', 'source-main-forge');
  await clickSlotWaitWindow(bot, 34, 'source-main-open-forge');
  const forgeChat = createChatWaiter(
    bot,
    8000,
    text => text.includes('Улучшение') || text.includes('Upgrade') || text.includes('донор') || text.includes('donor'),
    'forge-attempt'
  ).catch(error => ({ error: error.message }));
  await clickSlotWaitWindow(bot, 22, 'forge-upgrade-button');
  const forgeResult = await forgeChat;
  const afterWolfEggs = countWolfEggs(bot);
  log('forgeAfter', { afterWolfEggs, forgeResult, items: inventorySummary(bot) });
  if (beforeWolfEggs - afterWolfEggs < 2) {
    throw new Error(`forge did not consume donor wolf eggs as expected: before=${beforeWolfEggs} after=${afterWolfEggs}`);
  }
  await closeWindow(bot, 'forge-after-attempt');
}

async function boxGuiOpen(bot) {
  const beforeEggs = countEggs(bot);
  await openByCommand(bot, '/pet menu main', 'source-main-box');
  await clickSlotWaitWindow(bot, 16, 'source-main-open-box');
  await clickSlot(bot, 22, 'box-open-button', 1800);
  await delay(1200);
  const afterEggs = countEggs(bot);
  log('boxAfter', { beforeEggs, afterEggs, items: inventorySummary(bot) });
  if (afterEggs <= beforeEggs) {
    throw new Error(`box open did not add an egg item: before=${beforeEggs} after=${afterEggs}`);
  }
  await closeWindow(bot, 'box-after-open');
}

async function petGuiNavigation(bot) {
  await openByCommand(bot, '/pet', 'pet-overview');
  await clickSlotWaitWindow(bot, 17, 'pet-help-open');
  await closeWindow(bot, 'pet-help-close');
  await openByCommand(bot, '/pet', 'pet-overview-evolution');
  await clickSlotWaitWindow(bot, 13, 'pet-evolution-open');
  await closeWindow(bot, 'pet-evolution-close');
}

async function main() {
  const bot = mineflayer.createBot({ host, port, username, version, auth: 'offline' });
  bot.on('message', (jsonMsg, position) => log('chat', { position, text: jsonMsg.toString() }));
  bot.on('kicked', reason => log('kicked', { reason: String(reason) }));
  bot.on('error', error => log('botError', { message: error.message }));
  bot.on('end', reason => log('end', { reason: String(reason) }));
  bot.on('windowOpen', win => log('windowOpenEvent', { window: windowSummary(win) }));

  await waitForEvent(bot, 'spawn', 90000);
  log('spawn', {
    gamemode: bot.game?.gameMode ?? null,
    position: bot.entity?.position?.toString?.() ?? null
  });
  await delay(1500);

  await command(bot, '/vpc source set');
  await command(bot, `/vpc admin giveegg ${username} wolf common`, 2200);
  const core = bot.inventory.items().find(item => item.name === 'wolf_spawn_egg');
  if (!core) {
    throw new Error('wolf core not found after giveegg');
  }
  await bot.equip(core, 'off-hand');
  await delay(800);
  log('offhandBeforeSourceClick', {
    slot45: bot.inventory.slots[45] ? { name: bot.inventory.slots[45].name, displayName: bot.inventory.slots[45].displayName } : null
  });

  await openSourceByBlock(bot);
  await ensureNoWolfYet(bot);
  await closeWindow(bot, 'source-block-open-close');

  await petGuiNavigation(bot);
  await questGuiTurnIn(bot);
  await forgeGuiAttempt(bot);
  await boxGuiOpen(bot);

  await command(bot, '/vpc admin audit ' + username);
  await command(bot, '/vpc debugpet');
  await command(bot, '/stop', 1000);
  await delay(1000);
  bot.end('release gate smoke complete');
}

main().catch(error => {
  log('fatal', { message: error.message, stack: error.stack });
  process.exitCode = 1;
});
