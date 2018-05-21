package com.evolveum.midpoint.schrodinger.page.resource;

import com.codeborne.selenide.SelenideElement;
import com.evolveum.midpoint.schrodinger.component.resource.ResourcesTablePage;
import com.evolveum.midpoint.schrodinger.page.BasicPage;
import org.openqa.selenium.By;

import static com.codeborne.selenide.Selenide.$;

/**
 * Created by Viliam Repan (lazyman).
 */
public class ListResourcesPage extends BasicPage {

    public ResourcesTablePage<ListResourcesPage> table() {
        SelenideElement table = $(By.cssSelector(".box.boxed-table.object-resource-box"));

        return new ResourcesTablePage<>(this, table);
    }
}
