package pl.edu.praktyki.security

import groovy.transform.Canonical

@Canonical
class UserDto {
    String username
    String role
}