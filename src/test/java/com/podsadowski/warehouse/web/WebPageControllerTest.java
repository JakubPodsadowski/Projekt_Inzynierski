package com.podsadowski.warehouse.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(WebPageController.class)
class WebPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void overview_returnsPage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/overview"))
                .andExpect(content().string(containsString("overview-dashboard")))
                .andExpect(content().string(containsString("simulation-panel")));
    }

    @Test
    void departments_returnsPage() throws Exception {
        mockMvc.perform(get("/departments"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/departments"))
                .andExpect(content().string(containsString("departments-page")));
    }

    @Test
    void employees_returnsPage() throws Exception {
        mockMvc.perform(get("/employees"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/employees"));
    }

    @Test
    void relocations_returnsPage() throws Exception {
        mockMvc.perform(get("/relocations"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/relocations"));
    }

    @Test
    void inventory_returnsPage() throws Exception {
        mockMvc.perform(get("/inventory"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/inventory"))
                .andExpect(content().string(containsString("inventory-page")));
    }

    @Test
    void employeeView_returnsPage() throws Exception {
        mockMvc.perform(get("/employees/5/view"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/employee-view"));
    }
}
