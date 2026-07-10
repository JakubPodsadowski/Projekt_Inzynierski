package com.podsadowski.warehouse.domain.repository;

import com.podsadowski.warehouse.domain.entity.Department;
import com.podsadowski.warehouse.domain.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    List<Employee> findByCurrentDepartment(Department department);
}