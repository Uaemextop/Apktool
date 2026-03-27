/*
 *  Copyright (C) 2010 Ryszard Wiśniewski <brut.alll@gmail.com>
 *  Copyright (C) 2010 Connor Tumbleson <connor.tumbleson@gmail.com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package brut.androlib.res.table;

import brut.androlib.Config;
import brut.androlib.meta.ApkInfo;

import org.junit.*;
import static org.junit.Assert.*;

public class ResEntrySpecTest {
    private static ResTypeSpec sTypeSpec;

    @BeforeClass
    public static void beforeClass() throws Exception {
        Config config = new Config("TEST");
        ApkInfo apkInfo = new ApkInfo();
        ResTable table = new ResTable(apkInfo, config);
        ResPackageGroup group = table.addPackageGroup(0x7f, "com.test");
        ResPackage pkg = group.getBasePackage();
        sTypeSpec = pkg.addTypeSpec(1, "drawable");
    }

    @Test
    public void validEntryNameIsNotRenamed() {
        ResEntrySpec entry = new ResEntrySpec(sTypeSpec, 0, "ic_launcher");
        assertEquals("ic_launcher", entry.getName());
    }

    @Test
    public void dollarSignAtStartIsRenamed() {
        ResEntrySpec entry = new ResEntrySpec(sTypeSpec, 1, "$avd_hide_password__0");
        assertTrue(entry.getName().startsWith(ResEntrySpec.RENAMED_PREFIX));
    }

    @Test
    public void dollarSignInMiddleIsRenamed() {
        ResEntrySpec entry = new ResEntrySpec(sTypeSpec, 2, "avd$hide_password");
        assertTrue(entry.getName().startsWith(ResEntrySpec.RENAMED_PREFIX));
    }

    @Test
    public void dotAndHyphenAreAllowed() {
        ResEntrySpec entry = new ResEntrySpec(sTypeSpec, 3, "ic-launcher.round");
        assertEquals("ic-launcher.round", entry.getName());
    }

    @Test
    public void emptyNameIsRenamed() {
        ResEntrySpec entry = new ResEntrySpec(sTypeSpec, 4, "");
        assertTrue(entry.getName().startsWith(ResEntrySpec.RENAMED_PREFIX));
    }
}
