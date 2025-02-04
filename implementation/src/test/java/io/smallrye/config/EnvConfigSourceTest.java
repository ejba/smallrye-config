/*
 * Copyright 2017 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.smallrye.config;

import static io.smallrye.config.Converters.STRING_CONVERTER;
import static io.smallrye.config.KeyValuesConfigSource.config;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.StreamSupport;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.Test;

import io.smallrye.config.EnvConfigSource.EnvProperty;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2018 Red Hat inc.
 */
class EnvConfigSourceTest {
    @Test
    void conversionOfEnvVariableNames() {
        String envProp = System.getenv("SMALLRYE_MP_CONFIG_PROP");
        assertNotNull(envProp);

        ConfigSource cs = new EnvConfigSource();
        assertEquals(envProp, cs.getValue("SMALLRYE_MP_CONFIG_PROP"));
        // the config source returns only the name of the actual env variable
        assertTrue(cs.getPropertyNames().contains("SMALLRYE_MP_CONFIG_PROP"));

        assertEquals(envProp, cs.getValue("smallrye_mp_config_prop"));
        assertFalse(cs.getPropertyNames().contains("smallrye_mp_config_prop"));

        assertEquals(envProp, cs.getValue("smallrye.mp.config.prop"));
        assertTrue(cs.getPropertyNames().contains("smallrye.mp.config.prop"));

        assertEquals(envProp, cs.getValue("SMALLRYE.MP.CONFIG.PROP"));
        assertFalse(cs.getPropertyNames().contains("SMALLRYE.MP.CONFIG.PROP"));

        assertEquals(envProp, cs.getValue("smallrye-mp-config-prop"));
        assertFalse(cs.getPropertyNames().contains("smallrye-mp-config-prop"));

        assertEquals(envProp, cs.getValue("SMALLRYE-MP-CONFIG-PROP"));
        assertFalse(cs.getPropertyNames().contains("SMALLRYE-MP-CONFIG-PROP"));

        assertEquals("1234", cs.getValue("smallrye_mp_config_prop_lower"));
        assertTrue(cs.getPropertyNames().contains("smallrye_mp_config_prop_lower"));

        assertEquals("1234", cs.getValue("smallrye/mp/config/prop"));
    }

    @Test
    void profileEnvVariables() {
        assertNotNull(System.getenv("SMALLRYE_MP_CONFIG_PROP"));
        assertNotNull(System.getenv("_ENV_SMALLRYE_MP_CONFIG_PROP"));

        SmallRyeConfig config = new SmallRyeConfigBuilder().addDefaultSources().withProfile("env").build();

        assertEquals("5678", config.getRawValue("smallrye.mp.config.prop"));
    }

    @Test
    void empty() {
        SmallRyeConfig config = new SmallRyeConfigBuilder().addDefaultSources().build();
        assertThrows(NoSuchElementException.class, () -> config.getValue("SMALLRYE_MP_CONFIG_EMPTY", String.class));
        assertTrue(
                stream(config.getPropertyNames().spliterator(), false).collect(toList()).contains("SMALLRYE_MP_CONFIG_EMPTY"));

        ConfigSource envConfigSource = StreamSupport.stream(config.getConfigSources().spliterator(), false)
                .filter(configSource -> configSource.getName().equals("EnvConfigSource"))
                .findFirst()
                .get();

        assertEquals("", envConfigSource.getValue("SMALLRYE_MP_CONFIG_EMPTY"));
    }

    @Test
    void ordinal() {
        SmallRyeConfig config = new SmallRyeConfigBuilder().withSources(new EnvConfigSource()).build();
        ConfigSource configSource = config.getConfigSources().iterator().next();

        assertTrue(configSource instanceof EnvConfigSource);
        assertEquals(301, configSource.getOrdinal());
    }

    @Test
    void indexed() {
        Map<String, String> env = new HashMap<String, String>() {
            {
                put("INDEXED_0_", "foo");
                put("INDEXED_0__PROP", "bar");
                put("INDEXED_0__PROPS_0_", "0");
                put("INDEXED_0__PROPS_1_", "1");
            }
        };

        EnvConfigSource envConfigSource = new EnvConfigSource(env, 300);
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .addDefaultInterceptors()
                .withSources(envConfigSource)
                .build();

        assertTrue(config.getValues("indexed", String.class, ArrayList::new).contains("foo"));
        assertTrue(config.getValues("indexed[0].props", String.class, ArrayList::new).contains("0"));
        assertTrue(config.getValues("indexed[0].props", String.class, ArrayList::new).contains("1"));
    }

    @Test
    void numbers() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .addDefaultInterceptors()
                .withSources(new EnvConfigSource(Map.of("999_MY_VALUE", "foo", "_999_MY_VALUE", "bar"), 300))
                .build();

        assertEquals("foo", config.getRawValue("999.my.value"));
        assertEquals("foo", config.getRawValue("999_MY_VALUE"));
        assertEquals("bar", config.getRawValue("_999_MY_VALUE"));
        assertEquals("bar", config.getRawValue("%999.my.value"));
    }

    @Test
    void map() {
        Map<String, String> env = new HashMap<String, String>() {
            {
                put("TEST_LANGUAGE__DE_ETR__", "Einfache Sprache");
                put("TEST_LANGUAGE_PT_BR", "FROM ENV");
            }
        };

        EnvConfigSource envConfigSource = new EnvConfigSource(env, 300);
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .addDefaultInterceptors()
                .withSources(config("test.language.pt-br", "value"))
                .withSources(envConfigSource)
                .build();

        assertEquals("Einfache Sprache", config.getRawValue("test.language.\"de.etr\""));

        Map<String, String> map = config.getValuesAsMap("test.language", STRING_CONVERTER, STRING_CONVERTER);
        assertEquals(map.get("de.etr"), "Einfache Sprache");
        assertEquals(map.get("pt-br"), "FROM ENV");
    }

    @Test
    void envEquals() {
        assertTrue(EnvProperty.equals("", ""));
        assertTrue(EnvProperty.equals(" ", " "));
        assertFalse(EnvProperty.equals(" ", "foo.bar"));
        assertFalse(EnvProperty.equals(" ", "FOO_BAR"));
        assertFalse(EnvProperty.equals("foo.bar", ""));
        assertFalse(EnvProperty.equals("FOO_BAR", ""));

        assertFalse(EnvProperty.equals("BAR", "foo.bar"));
        assertFalse(EnvProperty.equals("foo.bar", "BAR"));

        assertTrue(EnvProperty.equals("FOO_BAR", "FOO_BAR"));
        assertTrue(EnvProperty.equals("FOO_BAR", "foo.bar"));
        assertTrue(EnvProperty.equals("FOO_BAR", "FOO.BAR"));
        assertTrue(EnvProperty.equals("FOO_BAR", "foo-bar"));
        assertTrue(EnvProperty.equals("FOO_BAR", "foo_bar"));

        assertTrue(EnvProperty.equals("foo.bar", "foo.bar"));
        assertTrue(EnvProperty.equals("foo.bar", "FOO_BAR"));
        assertTrue(EnvProperty.equals("FOO.BAR", "FOO_BAR"));
        assertTrue(EnvProperty.equals("foo-bar", "FOO_BAR"));
        assertTrue(EnvProperty.equals("foo_bar", "FOO_BAR"));

        assertTrue(EnvProperty.equals("FOO__BAR__BAZ", "foo.\"bar\".baz"));
        assertTrue(EnvProperty.equals("foo.\"bar\".baz", "FOO__BAR__BAZ"));

        assertTrue(EnvProperty.equals("FOO__BAR__BAZ_0__Z_0_", "foo.\"bar\".baz[0].z[0]"));

        assertTrue(EnvProperty.equals("_DEV_FOO_BAR", "%dev.foo.bar"));
        assertTrue(EnvProperty.equals("%dev.foo.bar", "_DEV_FOO_BAR"));
        assertTrue(EnvProperty.equals("_ENV_SMALLRYE_MP_CONFIG_PROP", "_ENV_SMALLRYE_MP_CONFIG_PROP"));
        assertTrue(EnvProperty.equals("%env.smallrye.mp.config.prop", "%env.smallrye.mp.config.prop"));
        assertTrue(EnvProperty.equals("_ENV_SMALLRYE_MP_CONFIG_PROP", "%env.smallrye.mp.config.prop"));
        assertTrue(EnvProperty.equals("%env.smallrye.mp.config.prop", "_ENV_SMALLRYE_MP_CONFIG_PROP"));

        assertTrue(EnvProperty.equals("indexed[0]", "indexed[0]"));
        assertTrue(EnvProperty.equals("INDEXED_0_", "INDEXED_0_"));
        assertTrue(EnvProperty.equals("indexed[0]", "INDEXED_0_"));
        assertTrue(EnvProperty.equals("INDEXED_0_", "indexed[0]"));

        assertTrue(EnvProperty.equals("env.\"quoted.key\".value", "env.\"quoted.key\".value"));
        assertTrue(EnvProperty.equals("ENV__QUOTED_KEY__VALUE", "ENV__QUOTED_KEY__VALUE"));
        assertTrue(EnvProperty.equals("ENV__QUOTED_KEY__VALUE", "env.\"quoted.key\".value"));
        assertTrue(EnvProperty.equals("env.\"quoted.key\".value", "ENV__QUOTED_KEY__VALUE"));
        assertTrue(EnvProperty.equals("env.\"quoted.key\".value", "env.\"quoted-key\".value"));
        assertTrue(EnvProperty.equals("env.\"quoted-key\".value", "env.\"quoted.key\".value"));

        assertTrue(EnvProperty.equals("TEST_LANGUAGE__DE_ETR__", "test.language.\"de.etr\""));
        assertTrue(EnvProperty.equals("test.language.\"de.etr\"", "TEST_LANGUAGE__DE_ETR__"));

        assertEquals(new EnvProperty("TEST_LANGUAGE__DE_ETR_").hashCode(),
                new EnvProperty("test.language.\"de.etr\"").hashCode());
    }
}
