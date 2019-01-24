// @skip-test
class Issue6 {
    Class<Enum<?>> c1;
    Class<Enum<?>> c2 = c1;
}
