package com.kartersanamo.havoc.command.subcommands.admin;

import org.junit.Assert;
import org.junit.Test;

public class AdminLogsParsedFilterTest {

    @Test
    public void defaultsAreStable() {
        AdminLogsParsedFilter f = new AdminLogsParsedFilter();
        Assert.assertFalse(f.ok);
        Assert.assertEquals("", f.user);
        Assert.assertEquals("", f.base);
        Assert.assertEquals("", f.type);
        Assert.assertEquals(1, f.page);
        Assert.assertEquals("all", f.scope);
        Assert.assertNull(f.from);
        Assert.assertNull(f.to);
    }

    @Test
    public void failCapturesErrorKey() {
        AdminLogsParsedFilter f = new AdminLogsParsedFilter();
        f.fail("admin.logs.usage");
        Assert.assertFalse(f.ok);
        Assert.assertEquals("admin.logs.usage", f.errorKey);
    }
}
