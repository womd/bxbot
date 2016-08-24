/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Gareth Jon Lynch
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.gazbert.bxbot.core.config.strategy;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/*
 * Tests a StrategyConfig domain object behaves as expected.
 */
public class TestStrategyConfig {

    private static final String ID = "macd-long-position";
    private static final String LABEL = "MACD Long Position Algo";
    private static final String DESCRIPTION = "Uses MACD as indicator and takes long position in base currency.";
    private static final String CLASSNAME = "com.gazbert.nova.algos.MacdLongBase";
    private static final Map<String, String> CONFIG_ITEMS = new HashMap<>();


    @Test
    public void testInitialisationWorksAsExpected() {

        final StrategyConfig strategyConfig = new StrategyConfig(ID, LABEL, DESCRIPTION, CLASSNAME, CONFIG_ITEMS);
        assertEquals(ID, strategyConfig.getId());
        assertEquals(LABEL, strategyConfig.getLabel());
        assertEquals(DESCRIPTION, strategyConfig.getDescription());
        assertEquals(CLASSNAME, strategyConfig.getClassName());
        assertEquals(CONFIG_ITEMS, strategyConfig.getConfigItems());
    }

    @Test
    public void testSettersWorkAsExpected() {

        final StrategyConfig strategyConfig = new StrategyConfig();
        assertEquals(null, strategyConfig.getId());
        assertEquals(null, strategyConfig.getLabel());
        assertEquals(null, strategyConfig.getDescription());
        assertEquals(null, strategyConfig.getClassName());
        assertEquals(null, strategyConfig.getConfigItems());

        strategyConfig.setId(ID);
        assertEquals(ID, strategyConfig.getId());

        strategyConfig.setLabel(LABEL);
        assertEquals(LABEL, strategyConfig.getLabel());

        strategyConfig.setDescription(DESCRIPTION);
        assertEquals(DESCRIPTION, strategyConfig.getDescription());

        strategyConfig.setClassName(CLASSNAME);
        assertEquals(CLASSNAME, strategyConfig.getClassName());

        strategyConfig.setConfigItems(CONFIG_ITEMS);
        assertEquals(CONFIG_ITEMS, strategyConfig.getConfigItems());
    }
}
