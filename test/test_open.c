#include "stdio.h"
#include "stdlib.h"

int main(int agrc, char *argv[]) {

  char* name = "hello";
  int fd = open(name);
  printf("fd: %d\n", fd);
  char* text = "hello world";
  write(fd, text, 5);
  int success = close(fd);
  printf("success: %d\n", success);
  return 0;
}
