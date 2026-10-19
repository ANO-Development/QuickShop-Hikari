package com.ghostchu.quickshop.shop;

import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.api.event.inventory.InventoryTransactionEvent;
import com.ghostchu.quickshop.api.inventory.InventoryWrapper;
import com.ghostchu.quickshop.api.inventory.ItemRemoveResult;
import com.ghostchu.quickshop.util.Util;
import com.ghostchu.quickshop.util.logger.Log;
import java.util.Map;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SimpleInventoryTransactionTest {

  @Test
  void transfersTheRemovedIdentitiesInsteadOfTheShopPrototype() {
    transfer(false);
  }

  @Test
  void failedInsertionRestoresBothInventorySnapshots() {
    transfer(true);
  }

  private void transfer(boolean full) {
    try(var quickShop = mockStatic(QuickShop.class); var util = mockStatic(Util.class);
        var log = mockStatic(Log.class); var events = mockConstruction(InventoryTransactionEvent.class)) {
      QuickShop plugin = mock(QuickShop.class, RETURNS_DEEP_STUBS);
      quickShop.when(QuickShop::getInstance).thenReturn(plugin);
      ItemStack prototype = item();
      ItemStack first = item();
      ItemStack second = item();
      ItemStack[] original = {first, second};
      ItemStack[] destination = {null, null};
      InventoryWrapper from = mock(InventoryWrapper.class);
      InventoryWrapper to = mock(InventoryWrapper.class);
      when(from.createSnapshot()).thenReturn(original);
      when(to.createSnapshot()).thenReturn(destination);
      when(from.removeItem(any(ItemStack[].class))).thenReturn(new ItemRemoveResult(Map.of(), Map.of(0, first, 1, second)));
      when(from.restoreSnapshot(original)).thenReturn(true);
      when(to.restoreSnapshot(destination)).thenReturn(true);
      when(to.addItem(any(ItemStack[].class))).thenAnswer(call -> full ? Map.of(0, (ItemStack) call.getArgument(0)) : Map.of());
      util.when(() -> Util.getItemMaxStackSizes(any(ItemStack[].class))).thenReturn(new int[]{1, 1});
      util.when(() -> Util.getItemMaxStackSize(any())).thenReturn(1);
      var transaction = new SimpleInventoryTransaction(from, to, prototype, 2);
      assertEquals(!full, transaction.commit(new SimpleInventoryTransaction.SimpleTransactionCallback() {}));
      if(full) {
        assertEquals(2, transaction.rollback(false).size());
        verify(from).restoreSnapshot(original);
        verify(to).restoreSnapshot(destination);
      } else {
        var transferred = ArgumentCaptor.forClass(ItemStack.class);
        verify(to, times(2)).addItem(transferred.capture());
        assertTrue(transferred.getAllValues().contains(first));
        assertTrue(transferred.getAllValues().contains(second));
        assertFalse(transferred.getAllValues().contains(prototype));
      }
    }
  }

  private ItemStack item() {
    ItemStack item = mock(ItemStack.class);
    when(item.clone()).thenReturn(item);
    when(item.getAmount()).thenReturn(1);
    when(item.getMaxStackSize()).thenReturn(1);
    return item;
  }
}
