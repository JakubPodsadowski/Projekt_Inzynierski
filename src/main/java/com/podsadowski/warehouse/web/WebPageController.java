package com.podsadowski.warehouse.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class WebPageController {

    @GetMapping("/")
    public String overview(Model model) {
        model.addAttribute("pageTitle", "Overview");
        model.addAttribute("activeMenu", "overview");
        return "pages/overview";
    }

    @GetMapping("/departments")
    public String departments(Model model) {
        model.addAttribute("pageTitle", "Departments");
        model.addAttribute("activeMenu", "departments");
        return "pages/departments";
    }

    @GetMapping("/employees")
    public String employees(Model model) {
        model.addAttribute("pageTitle", "Employees");
        model.addAttribute("activeMenu", "employees");
        return "pages/employees";
    }

    @GetMapping("/relocations")
    public String relocations(Model model) {
        model.addAttribute("pageTitle", "Relocations");
        model.addAttribute("activeMenu", "relocations");
        return "pages/relocations";
    }

    @GetMapping("/inventory")
    public String inventory(Model model) {
        model.addAttribute("pageTitle", "Inventory");
        model.addAttribute("activeMenu", "inventory");
        return "pages/inventory";
    }

    @GetMapping("/employees/{id}/view")
    public String employeeView(@PathVariable Long id, Model model) {
        model.addAttribute("pageTitle", "Employee");
        model.addAttribute("activeMenu", "employees");
        model.addAttribute("employeeId", id);
        return "pages/employee-view";
    }
}
