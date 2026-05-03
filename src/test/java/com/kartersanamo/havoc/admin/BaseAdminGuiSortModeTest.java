package com.kartersanamo.havoc.admin;

import org.junit.Assert;
import org.junit.Test;

public class BaseAdminGuiSortModeTest {

    @Test
    public void cyclesThroughAllModes() {
        Assert.assertEquals(BaseAdminGuiSortMode.DISTANCE, BaseAdminGuiSortMode.ACTIVE_FIRST.next());
        Assert.assertEquals(BaseAdminGuiSortMode.DIFFICULTY, BaseAdminGuiSortMode.DISTANCE.next());
        Assert.assertEquals(BaseAdminGuiSortMode.ACTIVE_FIRST, BaseAdminGuiSortMode.DIFFICULTY.next());
    }
}
