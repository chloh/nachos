#include "stdio.h"
#include "stdlib.h"

int main(int agrc, char *argv[]) {
  char* sort = "sort.o";
  char* sh = "sh.o";
  char* mv = "mv.o";
  char* halt = "halt.o";
  char* cp = "cp.o";

  char* name = "hello";
  int fd1 = open(name);
  int fd2 = open(sort);
  int fd3 = open(sh);
  int fd4 = open(mv);
  int fd5 = open(halt);
  int fd6 = open(cp);
  printf("fd1: %d\n", fd1);
  printf("fd2: %d\n", fd2);
  printf("fd3: %d\n", fd3);
  printf("fd4: %d\n", fd4);
  printf("fd5: %d\n", fd5);
  printf("fd6: %d\n", fd6);
  //char* text = "hello world";
  //write(fd, text, 5);
  //int success = close(fd);
  //printf("success: %d\n", success);
  return 0;
}
