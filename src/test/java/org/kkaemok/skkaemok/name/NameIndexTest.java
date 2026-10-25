package org.kkaemok.skkaemok.name;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class NameIndexTest {
    private final UUID steve = UUID.randomUUID(), alex = UUID.randomUUID();
    private NameIndex.Identity identity(UUID id, String account, String name, boolean visible) {
        return new NameIndex.Identity(id, account, name, Set.of(name), Component.text(name), visible);
    }
    @Test void realAccountsAndUuidsTakePrecedenceOverAliases() {
        NameIndex names = new NameIndex(List.of(identity(steve,"Steve","Alex",true),identity(alex,"Alex","모험가",true)));
        assertEquals(alex,names.resolve("Alex").id());
        assertEquals(steve,names.resolve(steve.toString()).id());
        assertNull(names.aliasAccount("sTeVe"));
        assertEquals("Steve",names.preferred(names.identity(steve)));
    }
    @Test void duplicatesDoNotResolveArbitrarily() {
        NameIndex names = new NameIndex(List.of(identity(steve,"Steve","깨묵",true),identity(alex,"Alex","깨묵",true)));
        assertNull(names.resolve("깨묵"));
        assertEquals("Steve",names.preferred(names.identity(steve)));
    }
    @Test void privateAndHiddenAliasesDoNotLeakBetweenViewers() {
        NameIndex a = new NameIndex(List.of(identity(steve,"Steve","Private",true)));
        NameIndex b = new NameIndex(List.of(identity(steve,"Steve","Global",true)));
        NameIndex hidden = new NameIndex(List.of(identity(steve,"Steve","Private",false)));
        assertEquals("Steve",a.aliasAccount("Private"));
        assertNull(b.resolve("Private")); assertNull(hidden.resolve("Private"));
        assertEquals(steve,hidden.resolve("Steve").id());
    }
    @Test void selectorsWildcardsAndFakeHoldersStayNative() {
        NameIndex names = new NameIndex(List.of(identity(steve,"Steve","모험가 이름",true)));
        for(String input:List.of("@a","@e[type=zombie,limit=1]","*","#counter","unknown")) assertNull(names.aliasAccount(input));
        assertEquals("Steve",names.aliasAccount("모험가 이름"));
        assertEquals("Steve",names.aliasAccount("Ｓｔｅｖｅ"));
    }
}
