#include "stdio.h"
#include "stdlib.h"

int main(int agrc, char *argv[]) {

  char* name = "hello";
  int fd = open(name);
  printf("fd: %d\n", fd);
  char* text;
  read(fd, text, 10);
  printf("%5s\n", text);
  return 0;
}
